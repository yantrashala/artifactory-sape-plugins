import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.repo.RepoPathFactory

executions{
	searchbykeyword(httpMethod: 'GET', users: 'readers'){ params ->

		try {

			def keywords = params?.get('keyword')
			def categories = params?.get('category')

			def aqlserv = ctx.beanForType(AqlService)

			log.debug(keywords)

			def names = keywords.collect { ['@module.name': ['$match': '*'+it+'*'], '@module.keywords': ['$match': '*'+it+'*'], '@module.team': ['$match': '*'+it+'*']]}
			def query = ['$or': names]
			def aql = "items.find(${new JsonBuilder(query).toString()})" +
					".include(\"*\")"

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.debug(aql.toString())

			def results = [];
			def json = [:]
			
			queryresults.each { aqlresult ->
				
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
				//item = repositories.getFileInfo(rpath)
				//FileLayoutInfo currentLayout = repositories.getLayoutInfo(rpath)
				//def name = ""
				//if (currentLayout.isValid()) name = currentLayout.module
                //else name = (item.name =~ '^(?:\\D[^.]*\\.)+')[0] - ~'\\.$'
                def properties  = repositories.getProperties(rpath)
                
				def result = [:]
				result['name'] = properties.get("module.name").getAt(0)
				result['version'] = properties.get("module.baseRevision").getAt(0)
				result['image'] = properties.get("module.image").getAt(0)
				
				results += result
			}
			
			json['results'] = results;

			message = new JsonBuilder(json).toPrettyString()
			status = 200

		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = e.message
			status = 500
		}

	}
}



