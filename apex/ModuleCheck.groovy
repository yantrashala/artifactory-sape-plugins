import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.repo.RepoPathFactory

executions{
	moduledetails(httpMethod: 'GET'){ params ->

		try {
			log.info("reached here")
			def module = params?.get('module').get(0)

			def aqlserv = ctx.beanForType(AqlService)

			
			def query = ['@module.name': module]
			def aql = "items.find(${new JsonBuilder(query).toString()})" +
					".include(\"*\")"

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.debug(aql.toString())

			def details = [:]
			def json = [:]
			
			log.info("result set size  "+queryresults.size())
			def itr = queryresults.iterator()
			while(itr.hasNext()){
				def itm = itr.next()
				log.info("item path : "+itm.path)
			}
			AqlBaseFullRowImpl aqlresult = queryresults.get(0)
				
			log.debug(aqlresult.toString())
				
			path = "$aqlresult.path/$aqlresult.name"
			rpath = RepoPathFactory.create(aqlresult.repo, path)
			item = repositories.getFileInfo(rpath)
			
			FileLayoutInfo currentLayout = repositories.getLayoutInfo(rpath)
			
			def properties  = repositories.getProperties(rpath)
			
			details['name'] = properties.get("module.name").getAt(0) ?: "N/A"
			details['version'] = properties.get("module.baseVersion").getAt(0) ?: "N/A"
			details['image'] = properties.get("module.image").getAt(0) ?: "N/A"
			details['publisher'] = aqlresult.getModifiedBy()
			details['lastModifiedOn'] = aqlresult.created.getTime()
			details['license'] = "BSD"
			details['scm'] = "tools.publicis.sapient.com/bitbucket-code-commons/"
			details['collaborators'] = ""
			details['readme'] =  properties.get("module.readme").getAt(0) ?: "N/A"
			details['gatekeepers'] = properties.get("module.gatekeepers").getAt(0) ?: "N/A"
			details['keywords']= properties.get("module.keywords").getAt(0) ?: "N/A"
			details['team']= properties.get("module.team").getAt(0) ?: "N/A"
			
			
			json['details'] = details;

			message = new JsonBuilder(json).toPrettyString()
			status = 200

		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = e.message
			status = 500
		}

	}
}


