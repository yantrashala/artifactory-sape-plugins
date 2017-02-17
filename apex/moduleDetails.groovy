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

			def module = params?.get('module').get(0)

			def aqlserv = ctx.beanForType(AqlService)

			
			def query = ['@model.name': module]
			def aql = "items.find(${new JsonBuilder(query).toString()})" +
					".include(\"*\")"

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.debug(aql.toString())

			def details = [:];
			
			def json = [:]
			
			AqlBaseFullRowImpl aqlresult = queryresults.get(0)
				
			log.debug(aqlresult.toString())
				
			path = "$aqlresult.path/$aqlresult.name"
			rpath = RepoPathFactory.create(aqlresult.repo, path)
			item = repositories.getFileInfo(rpath)
			FileLayoutInfo currentLayout = repositories.getLayoutInfo(rpath)
			
			details['name'] = currentLayout.module
			details['version'] = currentLayout.baseRevision
			details['image'] = "/artifactory/content/artworks/" + currentLayout.module + "/" + currentLayout.module + ".jpg"
			details['publisher'] = aqlresult.getModifiedBy()		
			details['lastModifiedOn'] = aqlresult.created.getTime()
			details['license'] = "BSD"
			details['scm'] = "tools.publicis.sapient.com/bitbucket-code-commons/"
			details['collaborators'] = ""
			details['readme'] = ""
			
			
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



