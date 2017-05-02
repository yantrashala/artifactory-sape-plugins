import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.repo.RepoPathFactory

executions{
	registryList(httpMethod: 'GET', groups : 'users'){ params ->

		try {
			def aqlserv = ctx.beanForType(AqlService)
			def query = ["@module.name":"*"]
			def aql = "items.find(${new JsonBuilder(query).toString()})"

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info(aql.toString())
			def checkList = [:]
			def results = [];
			queryresults.each { aqlresult ->
				rpath = "$aqlresult.repo" as String
				if(!checkList.containsKey(rpath)){
					checkList[rpath] = rpath
					results += rpath
				}
			}

			def json = [:]
			json['results'] = results;
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
