import groovy.json.JsonBuilder
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.repo.RepoPathFactory

executions{

	copyImageProperty(httpMethod: 'GET', groups : 'users'){ params ->
		try {
			// getting module as url parameters
			def module = params?.get('module')?.getAt(0) as String
			def baseModule = params?.get('module')?.getAt(1) as String

			def aqlserv = ctx.beanForType(AqlService)
			def names = [ ['@module.name': ['$match': baseModule]], ['@docker.repoName': ['$match': baseModule]] ]
			def query = ['$or': names]
			def aql = "items.find(${new JsonBuilder(query).toString()})" +
					".include(\"*\")"

			def queryresults = aqlserv.executeQueryEager(aql).results
			//def mlist = []
			def json = [:]
			if(queryresults.size() > 0) {
				AqlBaseFullRowImpl aqlresult = queryresults.get(0)
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
				def properties  = repositories.getProperties(rpath)
				def imagePath = properties.get("module.image").getAt(0)

				def mList = setImagePath(module, imagePath )
				if(mList.size() > 0)
					json['message'] = "Image property copied successfully for module(s) : "+mList
				else
					json['message'] = "No module(s) available in CodeCommonsApex : "+module.replaceAll('\\$',',')
			} else {
				json['message'] = "Base Module not available in CodeCommonsApex : "+baseModule
			}
			message = new JsonBuilder(json).toPrettyString()
		}catch (e) {
			log.error 'Failed to execute copyImageProperty plugin', e
			message = 'Failed to execute copyImageProperty plugin'
			status = 500
		}
	}

	imagePropertySetup(httpMethod: 'GET', groups : 'users'){ params ->
		try {
			// getting module as url parameters
			def module = params?.get('module')?.getAt(0)
			def fileName = params?.get('module')?.getAt(1)
			def moduleValue = params?.get('module')?.getAt(2)
			log.info('-----' + module + '-----' + fileName)

			def imagePath = "/artifactory/assets/images/"+moduleValue+"/"+fileName
			List list = setImagePath(module, imagePath )
			def json = [:]
			if(list.size() > 0)
				json['message'] = "Image setup successfully for module : "+list
			else
				json['message'] = "No module(s) available in CodeCommonsApex : "+module.replaceAll('\\$',',')
			message = new JsonBuilder(json).toPrettyString()
		}catch (e) {
			log.error 'Failed to execute imagePropertySetup plugin', e
			message = 'Failed to execute imagePropertySetup plugin'
			status = 500
		}
	}

	copyTeamFile(httpMethod: 'GET', groups : 'users') { params ->
		try {
			def team = params?.get('team')?.getAt(0)
			def teamFile = params?.get('team')?.getAt(1)
			def teamBackup = params?.get('team')?.getAt(2)
			asSystem {
				def repoCopy =  RepoPathFactory.create("assets","teams/backup/"+teamBackup )
				def repoCopyTarget =  RepoPathFactory.create("assets","/teams/"+team+"/"+teamFile)
				repositories.copy(repoCopy , repoCopyTarget )
				repositories.delete(repoCopy)
			}

			def json = [:]
			json['message'] = "Team File Uploaded successfully"
			message = new JsonBuilder(json).toPrettyString()
		}catch (e) {
			log.error 'Failed to execute copyTeamFile plugin', e
			message = 'Failed to execute copyTeamFile plugin'
			status = 500
		}
	}
}

private List setImagePath(module, imagePath) {
	def splitter = '$' as String
	def moduleList = module.tokenize(splitter)
	def names = []
	def mlist = []
	try {
		moduleList.each { list ->
			names.add('@module.name': ['$match': list ])
			names.add('@docker.repoName': ['$match': list ])
		}

		def query = ['$or': names]
		def aql = "items.find(${new JsonBuilder(query).toString()})" +
				".include(\"*\")"

		def aqlserv = ctx.beanForType(AqlService)
		def queryresults = aqlserv.executeQueryEager(aql).results
		log.info('--AQL-----'+aql.toString())

		queryresults.each { aqlresult ->
			log.info('inside queryresults loop')
			path = "$aqlresult.path/$aqlresult.name"
			rpath = RepoPathFactory.create(aqlresult.repo, path)
			def properties  = repositories.getProperties(rpath)

			def moduleName = properties.get("module.name").getAt(0) ?: properties.get("docker.repoName").getAt(0)
			def version = properties.get("nuget.version").getAt(0)?: properties.get("npm.version").getAt(0)?: properties.get("composer.version").getAt(0) ?:
						properties.get("module.baseRevision").getAt(0) ?: properties.get("docker.label.version").getAt(0) ?: "NA"

			repositories.setProperty(rpath, "module.image", imagePath)
			mlist += moduleName+":"+version
		}
	}catch (e) {
		log.error 'Failed to execute imagePropertySetup plugin', e
		message = 'Failed to execute imagePropertySetup plugin'
		status = 500
	}
	return mlist
}
