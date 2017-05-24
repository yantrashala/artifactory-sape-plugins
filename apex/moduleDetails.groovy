import groovy.json.JsonBuilder
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.repo.RepoPathFactory

executions{
	moduledetails(httpMethod: 'GET', groups : 'users'){ params ->

		try {
			// getting keyword as url parameters
			def module = params?.get('module').getAt(0)
			def version =   params?.get('module').getAt(1)
			if(version.equals("NA")){
				version = "*"
			}

			// AQL query to get the module details
			def aql = "items.find({\"\$and\":["+
						"{\"\$or\":[{\"@module.name\":{\"\$match\":\"$module\"}}, {\"@docker.repoName\":{\"\$match\":\"$module\"}}]},"+
						"{\"\$or\":[{\"@npm.version\":{\"\$match\":\"$version\"}},{\"@module.baseRevision\":{\"\$match\":\"$version\"}},{\"@composer.version\":{\"\$match\":\"$version\"}},{\"@docker.label.version\":{\"\$match\":\"$version\"}}]}"+
					  "]}).include(\"*\")"
			log.info('AQl query for module name and version : '+aql)

			def json = [:]
			json['details'] = getModuleDetails(aql);
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
}

private HashMap getModuleDetails(aql) {
	def details = [:]
	def aqlserv = ctx.beanForType(AqlService)
	try {
		def queryresults = aqlserv.executeQueryEager(aql).results
		log.info("result set size  "+queryresults.size())

		if(queryresults.size() > 0) {
			AqlBaseFullRowImpl aqlresult = queryresults.get(0)

			path = "$aqlresult.path/$aqlresult.name"
			rpath = RepoPathFactory.create(aqlresult.repo, path)

			// Getting the properties for the required module name
			def properties  = repositories.getProperties(rpath)
			def moduleName = properties.get("module.name").getAt(0) ?: properties.get("docker.repoName").getAt(0)

			details['name'] = moduleName
			details['version'] = properties.get("npm.version").getAt(0)?: properties.get("composer.version").getAt(0) ?:
						properties.get("module.baseRevision").getAt(0) ?: properties.get("docker.label.version").getAt(0) ?: "NA"
			details['image'] = properties.get("module.image").getAt(0) ?: "NA"
			details['publisher'] = aqlresult.getModifiedBy()
			details['lastModifiedOn'] = aqlresult.created.getTime()
			details['license'] = properties.get("artifactory.licenses").getAt(0) ?: ""
			details['scm'] = "tools.publicis.sapient.com/bitbucket-code-commons/"
			details['readme'] =  properties.get("module.readme").getAt(0)
			details['gatekeepers'] = properties.get("module.gatekeepers").getAt(0) ?: properties.get("docker.label.gatekeepers").getAt(0) ?: ""
			details['keywords']= properties.get("module.keywords").getAt(0) ?: properties.get("docker.label.keywords").getAt(0) ?: "NA"
			details['team']= properties.get("module.team").getAt(0) ?: properties.get("docker.label.team").getAt(0) ?: ""
			details['type']= properties.get("module.type").getAt(0) ?: properties.get("docker.label.type").getAt(0) ?: ""
			details['description'] = properties.get("npm.description").getAt(0) ?: properties.get("module.description").getAt(0) ?: properties.get("composer.description").getAt(0) ?: properties.get("docker.label.description").getAt(0) ?: ""
			details['versionHistory'] = getVersionHistory(moduleName)
		}

	} catch (e) {
		log.error 'Failed to execute getModuleDetails method in moduleDetails plugin', e
		message = 'Failed to execute getModuleDetails method in moduleDetails plugin'
		status = 500
	}
	return details
}

private List getVersionHistory(module) {
	// To get the version history for the given module, so firing a query to get all the versions
	def moduleNames = [ ['@module.name': ['$match': module]], ['@docker.repoName': ['$match': module]] ]
	def modquery = ['$or': moduleNames]
	def aqlSort = "items.find(${new JsonBuilder(modquery).toString()})" +
										".include(\"*\")" +
										".sort({\"\$desc\" : [\"created\"]})"
	def aqlserv = ctx.beanForType(AqlService)
	def sortQueryResults = aqlserv.executeQueryEager(aqlSort).results
	def results = [];
	try{
			// Loop to get all the versions for the modules
			sortQueryResults.each { sortaqlresult ->
			def result = [:]
		    	path = "$sortaqlresult.path/$sortaqlresult.name"
			repositorypath = RepoPathFactory.create(sortaqlresult.repo, path)
			def repoProperties  = repositories.getProperties(repositorypath)

			result['name'] = repoProperties.get("module.name").getAt(0) ?: repoProperties.get("docker.repoName").getAt(0) ?: "NA"
			result['version'] = repoProperties.get("npm.version").getAt(0) ?: repoProperties.get("composer.version").getAt(0) ?:
										repoProperties.get("module.baseRevision").getAt(0) ?: repoProperties.get("docker.label.version").getAt(0) ?: "NA"
			result['lastModifiedOn'] = sortaqlresult.created.getTime()
			results += result
		}
	} catch (e) {
		log.error 'Failed to execute getVersionHistory method', e
		message = 'Failed to execute getVersionHistory method'
		status = 500
	}
	return results
}
