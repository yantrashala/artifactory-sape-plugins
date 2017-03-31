import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.repo.RepoPathFactory

executions{
	searchbykeyword(httpMethod: 'GET', groups : 'users'){ params ->

		try {
			// getting keyword and category as url parameters
			def keywords = params['keyword'] ? params['keyword'][0] as String : "--NA--"
			
			def aqlserv = ctx.beanForType(AqlService)
			log.info(keywords.toLowerCase())

			// AQL query to match the keywords
			def names = keywords.collect { ['@module.name': ['$match': '*'+keywords.toLowerCase()+'*'], '@module.keywords': ['$match': '*'+keywords.toLowerCase()+'*'], '@module.team': ['$match': '*'+keywords.toLowerCase()+'*']]}
			def query = ['$or': names]
			
			def sub = ['$desc':["created"]]
			def aql = "items.find(${new JsonBuilder(query).toString()})" +
					".include(\"*\").sort(${new JsonBuilder(sub).toString()}).limit(51)"
			
			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info(aql.toString())
			log.info("this is query results object "+queryresults.size())
			
			def results = [];
			def result = [:]
			queryresults.each { aqlresult ->
								
				log.info("this is aql result props "+aqlresult.getAt("created").getTime())
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
                def properties  = repositories.getProperties(rpath)
               
				//creating the result JSON while checking whether the property is available or not
				
				/*if(repositories.hasProperty(rpath, "module.name"))
					result['name'] = properties.get("module.name").getAt(0)
				if(repositories.hasProperty(rpath, "module.baseRevision"))
					result['version'] = properties.get("module.baseRevision").getAt(0)
				if(repositories.hasProperty(rpath, "module.image"))
					result['image'] = properties.get("module.image").getAt(0)*/
				
				if(!result.containsValue(properties.get("module.name").getAt(0)) ){
					result = new HashMap()
					//log.info("this is created time "+aqlresult2.created.getTime())
					result['name'] = properties.get("module.name").getAt(0) ?: "N/A"
					result['version'] = properties.get("npm.version").getAt(0) ?: properties.get("composer.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: "N/A"
					result['image'] = properties.get("module.image").getAt(0) ?: "N/A"
					//result['created'] = aqlresult.getAt("created").getTime()
					log.info("has list has this map"+results.contains(result))
					
						results += result
					
					
				}else{
					log.info("reached else")
					result['version'] = properties.get("npm.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: "N/A"
				}
			}
			
			/*for (int i = 0; i < results.size(); i++) {
				for (int j = i+1; j < results.size(); j++) {
						if(results.get(i).getAt("name").equals(results.get(j).getAt("name"))){
							results.remove(j)
						}
				}
			}*/
			
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



