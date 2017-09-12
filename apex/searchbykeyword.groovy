import groovy.json.JsonBuilder
import org.artifactory.aql.AqlService
import org.artifactory.repo.RepoPathFactory

executions{
	
	/* artifactory/api/plugins/execute/searchbykeyword?params=keyword=<key_word> ,
	 * executes the closure if the request is from 'users' group
	 * Parameters:
	 * key_word - String given by the user to search for modules
	 * 
	 */
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
			
			//subquery to sort the modules by created date
			def sub = ['$desc':["created"]]
			
			//constructing  AQL query
			def aql = "items.find(${new JsonBuilder(queryList).toString()})" +
					".include(\"*\").sort(${new JsonBuilder(sub).toString()})"

			def json = [:]
			json['results'] = getResult(aql);
			// Creating the JSON Builder
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute searchbykeyword plugin', e
			message = 'Failed to execute searchbykeyword plugin'
			status = 500
		}
	}

	/*  artifactory/api/plugins/execute/searchbyteam?params=keyword=<team_name> ,
	 * executes the closure if the request is from 'users' group
	 * Parameters:
	 * team_name - Name of the team which deployed modules in AppExchange
	 *
	 */
	searchbyteam(httpMethod: 'GET', groups : 'users'){ params ->

		try {
			// getting teamname as keyword url parameters
			def keywords = params['keyword'] ? params['keyword'][0] as String : "--NA--"

			// AQL query to match the keywords
			def names = [ ['@module.team': ['$match': keywords.toLowerCase()]], ['@docker.label.team': ['$match': keywords.toLowerCase()]] ]
			def query = ['$or': names]
			def isApproved = ['@module.approved':['$eq': "true"]]
			def queryList  = ['$and':[query,isApproved]]
			
			//subquery to sort the modules by created date
			def sub = ['$desc':["created"]]
			
			//constructing  AQL query
			def aql = "items.find(${new JsonBuilder(queryList).toString()})" +
					".include(\"*\").sort(${new JsonBuilder(sub).toString()})"

			def json = [:]
			json['results'] = getResult(aql);
			// Creating the JSON Builder
			message = new JsonBuilder(json).toPrettyString()
			status = 200

		} catch (e) {
			log.error 'Failed to execute searchbyteam plugin', e
			message = 'Failed to execute searchbyteam plugin'
			status = 500
		}
	}

	/*  artifactory/api/plugins/execute/listbyrepo?params=reponame=<repo_name> ,
	 * executes the closure if the request is from 'users' group
	 * Parameters:
	 * repo_name - Name of one of the repo in AppExchange
	 *
	 */
	listbyrepo(httpMethod: 'GET', groups : 'users'){ params ->
		try{
			// getting reponame as url parameters
			def reponame = params['reponame'] ? params['reponame'][0] as String : "--NA--"

			// AQL query to match the reponame
			def names =  [["repo":reponame,'$or':[['@module.name':"*"],['@docker.repoName':"*"]]]]
			def query = ['$and': names]
			
			//subquery to sort the modules by created date
			def sub = ['$desc':["created"]]
			def isApproved = ['@module.approved':['$eq': "true"]]
			def queryList  = ['$and':[query,isApproved]]
			
			//constructing  AQL query
			def aql = "items.find(${new JsonBuilder(queryList).toString()})" +
					".include(\"*\").sort(${new JsonBuilder(sub).toString()})"

			def json = [:]
			json['results'] = getResult(aql);
			// Creating the JSON Builder
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute listbyrepo plugin', e
			message = 'Failed to execute listbyrepo plugin'
			status = 500
		}
	}
}

/*
 *
 * Parameters :
 * aql - AQL query 
 * returns - List of modules which matches the search query
 *
 */
private List getResult(aql) {

	def results = []
	def result = [:]
	def checkResult = [:]
	def aqlserv = ctx.beanForType(AqlService)

	try{
		
		//executing AQL query
		def queryresults = aqlserv.executeQueryEager(aql).results
		log.info(aql.toString())

		//Looping on Query results to get the list of modules
		queryresults.each { aqlresult ->
			path = "$aqlresult.path/$aqlresult.name"
			rpath = RepoPathFactory.create(aqlresult.repo, path)
	                def properties  = repositories.getProperties(rpath)
			def moduleNameCheck = properties.get("module.name").getAt(0) ?: properties.get("docker.repoName").getAt(0)

			// This condition is added so that repetitive modules can be removed
			if(!checkResult.containsKey(moduleNameCheck)) {
				result = new HashMap()
				result['name'] = moduleNameCheck
				result['version'] = properties.get("nuget.version").getAt(0) ?: properties.get("npm.version").getAt(0) ?: properties.get("composer.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: properties.get("docker.label.version").getAt(0) ?: "NA"
				result['image'] = properties.get("module.image").getAt(0) ?: ""
				result['team'] = properties.get("module.team").getAt(0) ?: properties.get("docker.label.team").getAt(0) ?: ""
				result['type']= properties.get("module.type").getAt(0) ?: properties.get("docker.label.type").getAt(0) ?: ""
				result['description'] = properties.get("nuget.description").getAt(0) ?: properties.get("npm.description").getAt(0) ?: properties.get("module.description").getAt(0) ?: properties.get("composer.description").getAt(0) ?: properties.get("docker.label.description").getAt(0) ?: ""

				checkResult[moduleNameCheck] = result
				results += result
			}
		}
	} catch (e) {
		log.error 'Failed to execute the getResult method', e
	}
	return results
}
