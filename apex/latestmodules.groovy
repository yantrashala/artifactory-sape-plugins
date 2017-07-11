import groovy.json.JsonBuilder
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.repo.RepoPathFactory

executions{
	latestmodules(httpMethod: 'GET', groups : 'users'){ params ->

		try {

			// AQL query to get the module details
			def sub = ['$desc':["created"]]
			def names = [['@module.name':"*"],['@docker.repoName':"*"]]
			def isApproved = ['@module.approved':['$eq': "true"]]
			def query = ['$or': names]
			def queryList  = ['$and':[query,isApproved]]
			def aql = "items.find(${new JsonBuilder(queryList).toString()}).sort(${new JsonBuilder(sub).toString()}).limit(12)"
			log.info('---------------AQL---------'+aql)

			def list = getResult(aql)
			message = new JsonBuilder(list).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
}

private List getResult(aql) {
	def list = []
	try {
		def aqlserv = ctx.beanForType(AqlService)
		def queryresults = aqlserv.executeQueryEager(aql).results
		log.info("result set size  "+queryresults.size())
		def details = [:]
		def checkResult = [:]
		for (AqlBaseFullRowImpl var in queryresults) {
			log.info("this is reppo path : "+var.path)
			path = "$var.path/$var.name"
			rpath = RepoPathFactory.create(var.repo, path)
			def properties  = repositories.getProperties(rpath)
			log.info("this is reppo path : "+var.path)

			if(!properties.isEmpty()){
				def moduleName = properties.get("module.name").getAt(0) ?: properties.get("docker.repoName").getAt(0)
				def isApproved  = properties.get("module.approved").getAt(0)
				if(!checkResult.containsKey(moduleName)){
					details = new HashMap()
					details['name'] =  properties.get("module.name").getAt(0) ?: properties.get("docker.repoName").getAt(0)
					details['version'] = properties.get("npm.version").getAt(0) ?: properties.get("composer.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: properties.get("docker.label.version").getAt(0) ?: "NA"
					details['image'] = properties.get("module.image").getAt(0) ?: ""
					details['team']= properties.get("module.team").getAt(0) ?: properties.get("docker.label.team").getAt(0) ?: ""
					details['description'] = properties.get("npm.description").getAt(0) ?: properties.get("module.description").getAt(0)  ?: properties.get("composer.description").getAt(0) ?: properties.get("docker.label.description").getAt(0) ?: ""
					details['repokey']=var.getRepo()
					checkResult[moduleName] = details
					list.add(details)
				}
			}
		}
	} catch (e) {
		log.error 'Failed to execute getResult method in latestmodules plugin', e
		message = 'Failed to execute getResult method in latestmodules plugin'
		status = 500
	}
	return list
}
