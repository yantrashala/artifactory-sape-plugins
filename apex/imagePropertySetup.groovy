import groovy.json.JsonBuilder

import java.time.chrono.AbstractChronology

import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.repo.RepoPathFactory

executions{
	imagePropertySetup(httpMethod: 'GET', groups : 'users'){ params ->
		
		try {
			// getting module as url parameters
			def module = params['module'] ? params['module'][0] as String : "NA"
			log.info('module-----' + module)
			def aqlserv = ctx.beanForType(AqlService)
				
			def names = module.collect { ['@module.name': ['$match': module.toLowerCase()]]}
			def query = ['$or': names]
			def aql = "items.find(${new JsonBuilder(query).toString()})" +
					".include(\"*\")"
			
			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info(aql.toString())
			
			queryresults.each { aqlresult ->
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
				def properties  = repositories.getProperties(rpath)
							
				def moduleName = properties.get("module.name").getAt(0)
				repositories.setProperty(rpath, "module.image", "/artifactory/assets/images/"+moduleName+"/"+moduleName+".jpg")
			}
			def json = [:]
			json['message'] = "Image uploaded successfully"
			message = new JsonBuilder(json).toPrettyString()
		}catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
}
