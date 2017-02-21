import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.repo.RepoPathFactory

executions{
	searchbykeyword(httpMethod: 'GET', users: 'readers'){ params ->

		try {
			// getting keyword and category as url parameters
			def keywords = params?.get('keyword').get(0)
			def categories = params?.get('category')

			def aqlserv = ctx.beanForType(AqlService)
			log.info(keywords)

			// AQL query to match the keywords
			def names = keywords.collect { ['@module.name': ['$match': '*'+keywords+'*'], '@module.keywords': ['$match': '*'+keywords+'*'], '@module.team': ['$match': '*'+keywords+'*']]}
			def query = ['$or': names]
			def aql = "items.find(${new JsonBuilder(query).toString()})" +
					".include(\"*\")"

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info(aql.toString())

			def results = [];
			
			queryresults.each { aqlresult ->
				
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
                def properties  = repositories.getProperties(rpath)
                
				//creating the result JSON while checking whether the property is available or not
				def result = [:]
				/*if(repositories.hasProperty(rpath, "module.name"))
					result['name'] = properties.get("module.name").getAt(0)
				if(repositories.hasProperty(rpath, "module.baseRevision"))
					result['version'] = properties.get("module.baseRevision").getAt(0)
				if(repositories.hasProperty(rpath, "module.image"))
					result['image'] = properties.get("module.image").getAt(0)*/
				
				result['name'] = properties.get("npm.name").getAt(0) ?: properties.get("module.name").getAt(0) ?: "N/A"
				result['version'] = properties.get("npm.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: "N/A"
				result['image'] = properties.get("module.image").getAt(0) ?: "N/A"
				
				results += result
			}
			
			def json = [:]
			json['results'] = results;
			// Creating the JSON Builder
			message = new JsonBuilder(json).toPrettyString()
			status = 200

		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = e.message
			status = 500
		}

	}
}



