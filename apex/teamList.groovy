import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.RepoPath
import org.artifactory.api.repo.RepositoryService
import org.artifactory.fs.FileInfo

executions{
	teamList(httpMethod: 'GET', groups : 'admin'){ params ->

		try {
			def aqlserv = ctx.beanForType(AqlService)
			def query = ["@module.team":"*"]
			def aql = "items.find(${new JsonBuilder(query).toString()})"

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info(aql.toString())
			def checkList = [:]
			def results = [];
			queryresults.each { aqlresult ->
				path = "$aqlresult.path/$aqlresult.name"
				rpath = RepoPathFactory.create(aqlresult.repo, path)
				def properties  = repositories.getProperties(rpath)

				def team = properties.get("module.team").getAt(0) as String
				if(!checkList.containsKey(team)){
					checkList[team] = team
					results += team
				}
			}

			def json = [:]
			json['results'] = results;
			// Creating the JSON Builder
			message = new JsonBuilder(json).toPrettyString()
			status = 200

			asSystem {
				// Get the RepoPath of the tags file and getting the FileInfo
				RepoPath repoPath = RepoPathFactory.create("content", "data/tags.json")
				RepositoryService repoService = ctx.beanForType(RepositoryService.class);
				FileInfo fileInfo= repoService.getFileInfo(repoPath)
				def str = repoService.getStringContent(fileInfo);

				// Converting the data from tags.json file in a json
				def jsonString = new JsonSlurper().parse(str.toCharArray())

				// Copying the new team Array in the existing json
				jsonString['team'] = results
				log.info('jsonString Complete--- '+jsonString);

				// Getting the file path and copying the updated json in tags.json file
				def filestoreDir = new File(ctx.artifactoryHome.dataDir, 'filestore')
				def sha1 = fileInfo.checksumsInfo.sha1
        def filestoreFile = new File(filestoreDir, "${sha1.substring(0, 2)}/$sha1")
				filestoreFile.write(new JsonBuilder(jsonString).toPrettyString())
			}

		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
}
