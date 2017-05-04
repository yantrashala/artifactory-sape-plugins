import groovy.json.JsonBuilder

import java.time.chrono.AbstractChronology

import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.fs.FileLayoutInfo
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
			def aqlserv = ctx.beanForType(AqlService)

			// AQL query to get the module details
			def aql = "items.find({\"\$and\":["+
						"{\"\$or\":[{\"@module.name\":{\"\$match\":\"$module\"}}]},"+
						"{\"\$or\":[{\"@npm.version\":{\"\$match\":\"$version\"}},{\"@module.baseRevision\":{\"\$match\":\"$version\"}},{\"@composer.version\":{\"\$match\":\"$version\"}}]}"+
					  "]}).include(\"*\")"
			log.info('AQl query for module name and version : '+aql)
			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info("result set size  "+queryresults.size())

			def details = [:]
			if(queryresults.size() > 0) {
				AqlBaseFullRowImpl aqlresult = queryresults.get(0)

				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)

				// Getting the properties for the required module name
				def properties  = repositories.getProperties(rpath)

				details['name'] = properties.get("module.name").getAt(0) ?: "NA"
				details['version'] = properties.get("npm.version").getAt(0) ?: properties.get("composer.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: "NA"
				details['image'] = properties.get("module.image").getAt(0) ?: "NA"
				details['publisher'] = aqlresult.getModifiedBy()
				details['lastModifiedOn'] = aqlresult.created.getTime()
				details['license'] = properties.get("artifactory.licenses").getAt(0) ?: "NA"
				details['scm'] = "tools.publicis.sapient.com/bitbucket-code-commons/"
				details['collaborators'] = ""
				details['readme'] =  properties.get("module.readme").getAt(0) ?: "NA"
				details['gatekeepers'] = properties.get("module.gatekeepers").getAt(0) ?: "NA"
				details['keywords']= properties.get("module.keywords").getAt(0) ?: "NA"
				details['organization'] = properties.get("module.organization").getAt(0) ?: "NA"
				details['team']= properties.get("module.team").getAt(0) ?: "NA"
				details['type']= properties.get("module.type").getAt(0) ?: "NA"

				// To get the version history for the given module, so firing a query to get all the versions
				def moduleNames = module.collect { ['@module.name': ['$match': module]]}
				def modquery = ['$or': moduleNames]
				def aqlSort = "items.find(${new JsonBuilder(modquery).toString()})" +
						".include(\"*\")" +
						".sort({\"\$desc\" : [\"created\"]})"
				def sortQueryResults = aqlserv.executeQueryEager(aqlSort).results
				def results = [];
				// Loop to get all the versions for the modules
				sortQueryResults.each { sortaqlresult ->
					def result = [:]
				    path = "$sortaqlresult.path/$sortaqlresult.name"
					repositorypath = RepoPathFactory.create(sortaqlresult.repo, path)
					def repoProperties  = repositories.getProperties(repositorypath)
					result['name'] = repoProperties.get("module.name").getAt(0) ?: "NA"
					result['version'] = repoProperties.get("npm.version").getAt(0) ?: repoProperties.get("composer.version").getAt(0) ?:
										repoProperties.get("module.baseRevision").getAt(0) ?: "NA"
					result['lastModifiedOn'] = sortaqlresult.created.getTime()
					results += result
				}
				details['versionHistory'] = results
			}
			def json = [:]
			json['details'] = details;
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
}
