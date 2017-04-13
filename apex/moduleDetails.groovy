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
			def version =   params?.get('module').getAt(1) ?: "*"
			def aqlserv = ctx.beanForType(AqlService)

			// AQL query to get the module details
			def aql = "items.find({\"\$and\":["+
						"{\"\$or\":[{\"@module.name\":{\"\$match\":\"$module\"}}]},"+
						"{\"\$or\":[{\"@npm.version\":{\"\$match\":\"$version\"}},{\"@module.baseRevision\":{\"\$match\":\"$version\"}},{\"@composer.version\":{\"\$match\":\"$version\"}}]}"+
					  "]}).include(\"*\")"
			log.info('---------------AQL---------'+aql)

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info("result set size  "+queryresults.size())

			def details = [:]
			if(queryresults.size() > 0) {
				AqlBaseFullRowImpl aqlresult = queryresults.get(0)
				log.info('---------------1--------------')
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
				
				// Getting the properties for the required module name
				def properties  = repositories.getProperties(rpath)
							
				details['name'] = properties.get("module.name").getAt(0) ?: "N/A"
				details['version'] = properties.get("npm.version").getAt(0)?: properties.get("composer.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: "N/A"
				details['image'] = properties.get("module.image").getAt(0) ?: "N/A"
				details['publisher'] = aqlresult.getModifiedBy()
				details['lastModifiedOn'] = aqlresult.created.getTime()
				details['license'] = properties.get("artifactory.licenses").getAt(0) ?: "N/A"
				details['scm'] = "tools.publicis.sapient.com/bitbucket-code-commons/"
				details['collaborators'] = ""
				details['readme'] =  properties.get("module.readme").getAt(0) ?: "N/A"
				details['gatekeepers'] = properties.get("module.gatekeepers").getAt(0) ?: "N/A"
				details['keywords']= properties.get("module.keywords").getAt(0) ?: "N/A"
				details['organization'] = properties.get("module.organization").getAt(0) ?: "N/A"
				details['team']= properties.get("module.team").getAt(0) ?: "N/A"
				details['type']= properties.get("module.type").getAt(0) ?: "N/A"
				log.info('---------------2--------------')
				// To get the version history for the given module, so firing a query to get all the versions
				def names1 = module.collect { ['@module.name': ['$match': module]]}
				def query1 = ['$or': names1]
				def aql1 = "items.find(${new JsonBuilder(query1).toString()})" +
						".include(\"*\")" + 
						".sort({\"\$desc\" : [\"created\"]})"
				def queryresults1 = aqlserv.executeQueryEager(aql1).results
				log.info("result set size  "+queryresults1.size())
				
				def results = [];
				// Loop to get all the versions for the modules
				queryresults1.each { aqlresult1 ->
					def result = [:]
					log.info('---------------3--------------')
					path1 = "$aqlresult1.path/$aqlresult1.name"
					rpath1 = RepoPathFactory.create(aqlresult1.repo, path1)
					def properties1  = repositories.getProperties(rpath1)
					
					result['name'] = properties1.get("module.name").getAt(0) ?: "N/A"
					result['version'] = properties1.get("npm.version").getAt(0) ?: properties1.get("composer.version").getAt(0) ?: properties1.get("module.baseRevision").getAt(0) ?: "N/A"
					result['lastModifiedOn'] = aqlresult1.created.getTime()
							
					results += result
				}
				details['versionHistory'] = results
			}
			log.info('---------------4--------------')
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
