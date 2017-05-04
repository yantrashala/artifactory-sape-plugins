import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.repo.RepoPathFactory
import java.io.File
import org.artifactory.repo.RepoPath
import org.artifactory.api.repo.RepositoryService
import org.artifactory.fs.FileInfo

jobs{
	teamlistupdate(cron: "10 12 11 1/1 * ? *") {
		try {
			def aqlserv = ctx.beanForType(AqlService)
			def query = ["@module.team":"*"]
			def aql = "items.find(${new JsonBuilder(query).toString()})"
			log.info('----1-----')
			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info(aql.toString())
			def checkList = [:]
			def results = [];
			queryresults.each { aqlresult ->
			log.info('----2-----')
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
				def properties  = repositories.getProperties(rpath)
				log.info('----3-----')
				def team = properties.get("module.team").getAt(0) as String
				if(!checkList.containsKey(team)){
				log.info('----4-----')
					checkList[team] = team
					results += team
				}
			}
			log.info('----5-----')
			def json = [:]
			json['heading'] = "Teams"
			json['url'] = "https://appx.tools.publicis.sapient.com/artifactory/api/plugins/execute/searchbykeyword?params=keyword="
			json['items'] = results
			// Creating the JSON Builder
			message = new JsonBuilder(json).toPrettyString()
			status = 200
			log.info('----6-----')

				// Get the RepoPath of the tags file and getting the FileInfo
				RepoPath repoPath = RepoPathFactory.create("content", "data/tags.json")
				RepositoryService repoService = ctx.beanForType(RepositoryService.class);
				FileInfo fileInfo= repoService.getFileInfo(repoPath)
				def str = repoService.getStringContent(fileInfo);

				// Converting the data from tags.json file in a json
				def jsonString = new JsonSlurper().parse(str.toCharArray())
				log.info('----7-----' + json)
				// Copying the new team Array in the existing json

				jsonString['teams'] = json
				log.info('jsonString Complete--- '+jsonString);
				log.info('----8-----')
				// Getting the file path and copying the updated json in tags.json file
				def filestoreDir = new File(ctx.artifactoryHome.dataDir, 'filestore')
				def sha1 = fileInfo.checksumsInfo.sha1
                		def filestoreFile = new File(filestoreDir, "${sha1.substring(0, 2)}/$sha1")
				log.info('---filestoreFile---'+filestoreFile)
				message = new JsonBuilder(jsonString).toPrettyString()
				filestoreFile.write(message)

			log.info('----9-----')
		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
}
