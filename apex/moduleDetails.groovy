import groovy.json.JsonBuilder
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.repo.RepoPathFactory
import org.artifactory.spring.InternalArtifactoryContext
import org.artifactory.api.context.ContextHelper
import org.artifactory.api.module.ModuleInfoUtils
import org.artifactory.api.repo.RepositoryService
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.dependecydeclaration.DependencyDeclaration
import org.artifactory.util.RepoLayoutUtils
import org.artifactory.spring.InternalArtifactoryContext
import org.artifactory.repo.RepoPath
import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.fs.StatsInfo


executions{

	// API: http://localhost:8081/artifactory/api/plugins/execute/moduledetails?params=usernamesss
	moduledetails(httpMethod: 'GET', groups : 'users'){ params ->

		try {
			// getting keyword as url parameters
			def module = params?.get('module').getAt(0)
			def version =   params?.get('module').getAt(1)
			if(version.equals("NA")){
				version = "*"
			}

			// AQL query to get the module details
			def aql = "items.find({\"\$and\":["+
						"{\"\$or\":[{\"@module.name\":{\"\$match\":\"$module\"}}, {\"@docker.repoName\":{\"\$match\":\"$module\"}}]},"+
						"{\"\$or\":[{\"@nuget.version\":{\"\$match\":\"$version\"}},{\"@npm.version\":{\"\$match\":\"$version\"}},{\"@module.baseRevision\":{\"\$match\":\"$version\"}},{\"@composer.version\":{\"\$match\":\"$version\"}},{\"@docker.label.version\":{\"\$match\":\"$version\"}}]}"+
					  "]}).include(\"*\")"

			log.debug('AQl query for module name and version : '+aql)

			// initialize a hashmap and executeAQL to get details
			def json = [:]
			json['details'] = getModuleDetails(aql);

			// convert hashmap to json
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to retrieve module details'
			status = 500
		}
	}
}

private HashMap getModuleDetails(aql) {
	def details = [:]

	// get aqlservice from context
	def aqlserv = ctx.beanForType(AqlService)

	// execute aql query and get results
	def queryresults = aqlserv.executeQueryEager(aql)?.results
	log.debug("result set size  "+queryresults.size())

	if(!queryresults && queryresults.size() > 0) {
		AqlBaseFullRowImpl aqlresult = queryresults.get(0)

		path = "$aqlresult.path/$aqlresult.name"
		rpath = RepoPathFactory.create(aqlresult.repo, path)
		String fullPath = rpath.getRepoKey()+"/"+rpath.getPath()
		LocalRepositoryConfiguration repoConfig = repositories.getRepositoryConfiguration(rpath.getRepoKey())
		// Getting the properties for the required module name
		def properties  = repositories.getProperties(rpath)
		def moduleName = properties.get("module.name").getAt(0) ?: properties.get("docker.repoName").getAt(0)
		def downloadCount = getModuleDownloadCount(rpath)
		def moduleType = properties.get("module.distribution").getAt(0) ?:  ""
		if(!moduleType.equals("") && moduleType.equals("true")){
			moduleType = "distribution"
		}else if(!moduleType.equals("") && moduleType.equals("false")) {
			moduleType = "artifact"
		}



		details['name'] = moduleName
		details['version'] = properties.get("nuget.version").getAt(0)?:  properties.get("npm.version").getAt(0)?: properties.get("composer.version").getAt(0) ?:
					properties.get("module.baseRevision").getAt(0) ?: properties.get("docker.label.version").getAt(0) ?: "NA"
		details['image'] = properties.get("module.image").getAt(0) ?: "NA"
		if(properties.get("module.organization").getAt(0)!= null )
			details['organization'] = properties.get("module.organization").getAt(0)
		if(properties.get("module.scm").getAt(0)!=null)
			details['scm'] = properties.get("module.scm").getAt(0)
		details['publisher'] = aqlresult.getModifiedBy()
		details['lastModifiedOn'] = aqlresult.created.getTime()
		details['license'] = properties.get("artifactory.licenses").getAt(0) ?: ""
		details['readme'] =  properties.get("module.readme").getAt(0)
		details['gatekeepers'] = properties.get("module.gatekeepers").getAt(0) ?: properties.get("docker.label.gatekeepers").getAt(0) ?: ""
		details['keywords']= properties.get("module.keywords").getAt(0) ?: properties.get("docker.label.keywords").getAt(0) ?: "NA"
		details['team']= properties.get("module.team").getAt(0) ?: properties.get("docker.label.team").getAt(0) ?: ""
		details['type']= moduleType
		details['description'] = properties.get("nuget.description.description").getAt(0) ?: properties.get("npm.description").getAt(0) ?: properties.get("module.description").getAt(0) ?: properties.get("composer.description").getAt(0) ?: properties.get("docker.label.description").getAt(0) ?: ""
		details['versionHistory'] = getVersionHistory(moduleName)
		details['downloadCount'] = downloadCount
		details['repokey'] = rpath.getRepoKey()
		details['download'] = fullPath
		if(repoConfig.getPackageType().equalsIgnoreCase("Maven")){
			String mavenDependency = getRepoLayout(ctx,rpath)
			details['dependency'] = mavenDependency
		}
		else if(repoConfig.getPackageType().equalsIgnoreCase("Npm"))
			details['dependency'] = "npm install "+moduleName
		else if(repoConfig.getPackageType().equalsIgnoreCase("Composer"))
			details['dependency'] = "composer install "+moduleName
		else if(repoConfig.getPackageType().equalsIgnoreCase("NuGet"))
			details['dependency'] = "Install-Package "+moduleName
	}

	return details
}
private String getRepoLayout(InternalArtifactoryContext  ctx,RepoPath repopath){
	String itemPath = repopath.getPath()
	def repoLayout = null
	if(repopath.getRepoKey().equals("maven-release") || repopath.getRepoKey().equals("maven-snapshot"))
		repoLayout  = ctx.getCentralConfig().getDescriptor().getRepoLayout(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME)
	def moduleInfo = ModuleInfoUtils.moduleInfoFromArtifactPath(itemPath,repoLayout)
	String mavenDependency = new DependencyDeclaration().getMavenDependencyDeclaration(moduleInfo)
	return mavenDependency
}
private List getVersionHistory(module) {
	// To get the version history for the given module, so firing a query to get all the versions
	def moduleNames = [ ['@module.name': ['$match': module]], ['@docker.repoName': ['$match': module]] ]
	def modquery = ['$or': moduleNames]
	def aqlSort = "items.find(${new JsonBuilder(modquery).toString()})" +
										".include(\"*\")" +
										".sort({\"\$desc\" : [\"created\"]})"
	def aqlserv = ctx.beanForType(AqlService)
	def sortQueryResults = aqlserv.executeQueryEager(aqlSort).results
	def results = [];
	try{
			// Loop to get all the versions for the modules
			sortQueryResults.each { sortaqlresult ->
			def result = [:]
		    	path = "$sortaqlresult.path/$sortaqlresult.name"
			repositorypath = RepoPathFactory.create(sortaqlresult.repo, path)
			def repoProperties  = repositories.getProperties(repositorypath)

			result['name'] = repoProperties.get("module.name").getAt(0) ?: repoProperties.get("docker.repoName").getAt(0) ?: "NA"
			result['version'] = repoProperties.get("nuget.version").getAt(0) ?: repoProperties.get("npm.version").getAt(0) ?: repoProperties.get("composer.version").getAt(0) ?:
										repoProperties.get("module.baseRevision").getAt(0) ?: repoProperties.get("docker.label.version").getAt(0) ?: "NA"
			result['lastModifiedOn'] = sortaqlresult.created.getTime()
			results += result
		}
	} catch (e) {
		log.error 'Failed to execute getVersionHistory method', e
		message = 'Failed to execute getVersionHistory method'
		status = 500
	}
	return results
}
private long getModuleDownloadCount(RepoPath repoPath){
	long count = 0
	RepositoryService repoService = ContextHelper.get().beanForType(RepositoryService.class)
	if(repoService !=null & repoService.getStatsInfo(repoPath)!=null){
		StatsInfo statsInfo = repoService.getStatsInfo(repoPath)
		count = statsInfo.getDownloadCount()
	}
	return count
}
