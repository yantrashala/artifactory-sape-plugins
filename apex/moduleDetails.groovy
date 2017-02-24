import groovy.json.JsonBuilder
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.repo.RepoPathFactory

executions{
	moduledetails(httpMethod: 'GET', groups : 'users'){ params ->

		try {
			// getting name and version as url parameters
			def module = params?.get('module').getAt(0)
			def version =   params?.get('module').getAt(1) ?: "*"
			def aqlserv = ctx.beanForType(AqlService)

			// AQL query to get the module details
			//def query = ['@module.name': module]
			/*def names = module.collect { ['@module.name': ['$match': module], '@npm.name': ['$match': module]]}
			def query = ['$or': names]
			def aql = "items.find(${new JsonBuilder(query).toString()})" +
					".include(\"*\")"*/
			
			def aql = "items.find({\"\$and\":["+
					"{\"\$or\":[{\"@module.name\":{\"\$match\":\"$module\"}},{\"@npm.name\":{\"\$match\":\"$module\"}}]},"+
					"{\"\$or\":[{\"@npm.version\":{\"\$match\":\"$version\"}},{\"@module.baseRevision\":{\"\$match\":\"$version\"}}]}"+
				  "]}).include(\"*\")"

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info(aql.toString())

			log.info("result set size  "+queryresults.size())
			/*def itr = queryresults.iterator()
			while(itr.hasNext()){
				def itm = itr.next()
				log.info("item path : "+itm.path)
			}*/
			def details = [:]
			if(queryresults.size() > 0) {
				AqlBaseFullRowImpl aqlresult = queryresults.get(0)
						
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
				
				// Getting the properties for the required module name
				def properties  = repositories.getProperties(rpath)
				def details = [:]
				details['name'] = properties.get("npm.name").getAt(0) ?: properties.get("module.name").getAt(0) ?: "N/A"
				details['version'] = properties.get("npm.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: "N/A"
				details['image'] = properties.get("module.image").getAt(0) ?: "N/A"
				details['publisher'] = aqlresult.getModifiedBy()
				details['lastModifiedOn'] = aqlresult.created.getTime()
				details['license'] = properties.get("artifactory.licenses").getAt(0) ?: "N/A"
				details['scm'] = "tools.publicis.sapient.com/bitbucket-code-commons/"
				details['collaborators'] = ""
				details['readme'] =  properties.get("module.readme").getAt(0) ?: "N/A"
				details['gatekeepers'] = properties.get("module.gatekeepers").getAt(0) ?: "N/A"
				details['keywords']= properties.get("module.keywords").getAt(0) ?: "N/A"
				details['team']= properties.get("module.team").getAt(0) ?: "N/A"
			}
			
			def json = [:]
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


