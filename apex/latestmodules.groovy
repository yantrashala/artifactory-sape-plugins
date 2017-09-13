import groovy.json.JsonBuilder
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.repo.RepoPathFactory
import searchbykeyword
import groovy.transform.Field

@Field final def SEARCH_BY_KEYWORD = new searchbykeyword()
executions{
	
	/* /artifactory/api/plugins/execute/latestmodules ,
	 * executes the closure if the request is from 'users' group
	 */
	latestmodules(httpMethod: 'GET', groups : 'users'){ params ->

		try {

			// AQL query to get the module details
			def names = [['@module.name':"*"],['@docker.repoName':"*"]]
			def isApproved = ['@module.approved':['$eq': "true"]]
			def query = ['$or': names]
			
			//sorting modules by created date
			def sub = ['$desc':["created"]]
			def queryList  = ['$and':[query,isApproved]]
			
			//constructing  AQL query
			def aql = "items.find(${new JsonBuilder(queryList).toString()}).sort(${new JsonBuilder(sub).toString()}).limit(12)"
			log.info('---------------AQL---------'+aql)
			
			//calling getResult method in searchbykeyword plugin
			def list = SEARCH_BY_KEYWORD.getResult(aql)
			message = new JsonBuilder(list).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute latestmodules plugin', e
			message = 'Failed to execute latestmodules plugin'
			status = 500
		}
	}
}

