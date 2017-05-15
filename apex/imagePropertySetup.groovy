import groovy.json.JsonBuilder
import org.artifactory.aql.AqlService
import org.artifactory.repo.RepoPathFactory

executions{
	imagePropertySetup(httpMethod: 'GET', groups : 'users'){ params ->
		try {
			// getting module as url parameters
			def module = params['module'] ? params['module'][0] as String : "NA"
			def fileName = params?.get('module')?.getAt(1)
			log.info('-----' + module + '-----' + fileName)

			def aqlserv = ctx.beanForType(AqlService)

			def names = [ ['@module.name': ['$match': module.toLowerCase()]], ['@docker.repoName': ['$match': module.toLowerCase()]] ]
			def query = ['$or': names]
			def aql = "items.find(${new JsonBuilder(query).toString()})" +
					".include(\"*\")"

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info(aql.toString())

			// Flag to check whether there are any modules present in artifactory with the provide module name
			def flag = 0
			queryresults.each { aqlresult ->
				log.info('inside queryresults loop')
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
				def properties  = repositories.getProperties(rpath)

				def moduleName = properties.get("module.name").getAt(0) ?: properties.get("docker.repoName").getAt(0)
				repositories.setProperty(rpath, "module.image", "/artifactory/assets/images/"+moduleName+"/"+fileName)
				flag = 1
			}
			def json = [:]
			if(flag)
				json['message'] = "Image setup successfully for module : "+module
			else
				json['message'] = "No module available with name : "+module
			message = new JsonBuilder(json).toPrettyString()
		}catch (e) {
			log.error 'Failed to execute imagePropertySetup plugin', e
			message = 'Failed to execute imagePropertySetup plugin'
			status = 500
		}
	}
}
