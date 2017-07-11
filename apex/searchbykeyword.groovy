import groovy.json.JsonBuilder
import org.artifactory.aql.AqlService
import org.artifactory.repo.RepoPathFactory

executions{
	// Search by Keyword execution
	searchbykeyword(httpMethod: 'GET', groups : 'users'){ params ->

		try {
			// getting keyword and category as url parameters
			def keywords = params['keyword'] ? params['keyword'][0] as String : "--NA--"

			// AQL query to match the keywords
			def key = keywords.toLowerCase()
			def names = [ ['@module.name': ['$match': '*'+key +'*']], ['@docker.repoName': ['$match': '*'+key +'*']], ['@docker.label.keywords': ['$match': '*'+key +'*']], ['@docker.label.team': ['$match': '*'+key +'*']] , ['@module.keywords': ['$match': '*'+key +'*']], ['@module.team': ['$match': '*'+key +'*']]]
			def query = ['$or': names]
			def isApproved = ['@module.approved':['$eq': "true"]]
			def queryList  = ['$and':[query,isApproved]]
			def sub = ['$desc':["created"]]
			def aql = "items.find(${new JsonBuilder(queryList).toString()})" +
					".include(\"*\").sort(${new JsonBuilder(sub).toString()})"

			def json = [:]
			json['results'] = getResult(aql);
			// Creating the JSON Builder
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}

	// Search by Team execution
	searchbyteam(httpMethod: 'GET', groups : 'users'){ params ->

		try {
			// getting teamname as keyword url parameters
			def keywords = params['keyword'] ? params['keyword'][0] as String : "--NA--"

			// AQL query to match the keywords
			def names = [ ['@module.team': ['$match': keywords.toLowerCase()]], ['@docker.label.team': ['$match': keywords.toLowerCase()]] ]
			def query = ['$or': names]
			def isApproved = ['@module.approved':['$eq': "true"]]
			def queryList  = ['$and':[query,isApproved]]
			def sub = ['$desc':["created"]]
			def aql = "items.find(${new JsonBuilder(queryList).toString()})" +
					".include(\"*\").sort(${new JsonBuilder(sub).toString()})"

			def json = [:]
			json['results'] = getResult(aql);
			// Creating the JSON Builder
			message = new JsonBuilder(json).toPrettyString()
			status = 200

		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}

	// Search by repo execution
	listbyrepo(httpMethod: 'GET', groups : 'users'){ params ->
		try{
			// getting reponame as url parameters
			def reponame = params['reponame'] ? params['reponame'][0] as String : "--NA--"

			// AQL query to match the reponame
			def names =  [["repo":reponame,'$or':[['@module.name':"*"],['@docker.repoName':"*"]]]]
			def query = ['$and': names]
			def sub = ['$desc':["created"]]
			def isApproved = ['@module.approved':['$eq': "true"]]
			def queryList  = ['$and':[query,isApproved]]
			def aql = "items.find(${new JsonBuilder(queryList).toString()})" +
					".include(\"*\").sort(${new JsonBuilder(sub).toString()})"

			def json = [:]
			json['results'] = getResult(aql);
			// Creating the JSON Builder
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
}


private List getResult(aql) {

	def results = []
	def result = [:]
	def checkResult = [:]
	def aqlserv = ctx.beanForType(AqlService)

	try{
		def queryresults = aqlserv.executeQueryEager(aql).results
		log.info(aql.toString())

		// Looping the Query results to get the list of modules
		queryresults.each { aqlresult ->
			path = "$aqlresult.path/$aqlresult.name"
			rpath = RepoPathFactory.create(aqlresult.repo, path)
	                def properties  = repositories.getProperties(rpath)
			def moduleNameCheck = properties.get("module.name").getAt(0) ?: properties.get("docker.repoName").getAt(0)

			// This condition is added so that repetitive modules can be removed
			if(!checkResult.containsKey(moduleNameCheck)) {
				result = new HashMap()
				result['name'] = moduleNameCheck
				result['version'] = properties.get("npm.version").getAt(0) ?: properties.get("composer.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: properties.get("docker.label.version").getAt(0) ?: "NA"
				result['image'] = properties.get("module.image").getAt(0) ?: ""
				result['team'] = properties.get("module.team").getAt(0) ?: properties.get("docker.label.team").getAt(0) ?: ""
				result['type']= properties.get("module.type").getAt(0) ?: properties.get("docker.label.type").getAt(0) ?: ""
				result['description'] = properties.get("npm.description").getAt(0) ?: properties.get("module.description").getAt(0) ?: properties.get("composer.description").getAt(0) ?: properties.get("docker.label.description").getAt(0) ?: ""

				checkResult[moduleNameCheck] = result
				results += result
			}
		}
	} catch (e) {
		log.error 'Failed to execute the getResult method', e
		message = 'Failed to execute the getResult method'
		status = 500
	}
	return results
}
