import java.nio.file.AccessDeniedException
import java.sql.ResultSet
import org.artifactory.util.AlreadyExistsException
import org.artifactory.util.ZipUtils
import org.artifactory.addon.AddonsManager
import org.artifactory.addon.composer.ComposerAddon
import org.artifactory.addon.composer.ComposerInfo
import org.artifactory.addon.composer.ComposerMetadataInfo
import org.artifactory.addon.npm.NpmInfo
import org.artifactory.addon.npm.NpmMetadataInfo
import org.artifactory.addon.nuget.UiNuGetAddon
import org.artifactory.api.context.ContextHelper
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.md.Properties
import org.artifactory.nuget.NuMetaData
import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.repo.RepoPath
import org.artifactory.repo.StoringRepo
import org.artifactory.repo.service.InternalRepositoryService
import org.artifactory.rest.common.exception.UnauthorizedException
import org.artifactory.sapi.fs.MutableVfsFile
import org.artifactory.spring.InternalArtifactoryContext
import org.artifactory.storage.db.DbService
import org.artifactory.storage.db.util.JdbcHelper
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.composer.ComposerArtifactInfo
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.nugetinfo.NugetArtifactInfo
import org.artifactory.addon.sso.ArtifactoryCrowdClient
import org.artifactory.api.maven.MavenArtifactInfo
import org.artifactory.addon.npm.NpmAddon
import groovy.json.JsonBuilder
import groovy.text.markup.AutoNewLineTransformer
import groovy.transform.Field
import userdetails

@Field JdbcHelper jdbcHelper = ctx.beanForType(JdbcHelper.class)
@Field InternalRepositoryService repositoryService = ctx.beanForType(InternalRepositoryService.class)
@Field DbService dbService = ctx.beanForType(DbService.class)
storage {
	afterCreate { ItemInfo item ->
		createPermissionForCurrentBuild(item)
	}
}
executions{
	addpublisher(httpMethod: 'POST',  groups: 'users'){ params ->
		def json = [:]
		
		try{
			def successList = []
			def invalidList = []
			def existingList = []
			def artifactoryCrowdClient= ctx.beanForType(ArtifactoryCrowdClient.class);
			if(!artifactoryCrowdClient.isCrowdAvailable()){
				throw new Exception("Crowd client is not available")
			}
			def pars = params?.getAt("publisher")
			for (var in pars) {
				def username = var
				def modulename = params?.getAt('module').getAt(0)
				
				if(validatePublisher(artifactoryCrowdClient,var)){
					log.info("username : "+username)
					log.info("modulename : "+modulename)
					def result = addPublisher(username,modulename)
					log.info("this is result "+result)
					if(result == 1){
						successList.add(var)
					}
					if(result == 0){
						existingList.add(var)
					}
					if(result == -1){
						throw new AccessDeniedException(security.getCurrentUsername()+",is not an author for the module"+ modulename+" to provide access")
						
					}
					
					
				}else{
					def response = "User " +var+ " does not exist"
					invalidList.add(var)
					
				}
			}
			def str = ""
			if(successList.size()>0)
				//json["Successfully added publishers"] = successList
				str += "Successfully added publishers : "+ successList + "\\n"
			if(invalidList.size()>0)
				//json["Invalid publisher names"] = invalidList
				str += "Invalid publisher names : "+ invalidList + "\\n"
			if(existingList.size()>0)
				//json["Existing publisher names"] = existingList
				str += "Successfully added publishers : "+ existingList + "\\n"
			
			
			message = new JsonBuilder(str).toPrettyString()
			status = 200
		}catch (e) {
			log.error 'Failed to execute plugin', e
			message = e.getMessage()
			status = 500
			
		}
	}
}
executions{
	removepublisher(httpMethod: 'DELETE',  groups: 'users'){ params ->
		def json = [:]
		try{
			def successList = []
			def invalidList = []
			def doesntexistList = []
			def artifactoryCrowdClient= ctx.beanForType(ArtifactoryCrowdClient.class);
			if(!artifactoryCrowdClient.isCrowdAvailable()){
				throw new Exception("Crowd client is not available")
			}
			String response = ""
			def pars = params?.getAt('remove')
			for(var in pars){
				def username = var
				def modulename = params?.getAt('module').getAt(0)
				if(validatePublisher(ctx,var)){
					int result = removePublisher(username,modulename)
					if(result == 1)
						successList.add(var)
					if(result == 0)
						doesntexistList.add(var)
				}else{
					invalidList.add(var)
				}
				
			}
			if(successList.size()>0)
				json["Successfully removed publishers"] = successList
			if(invalidList.size()>0)
				json["Invalid publisher names"] = invalidList
			if(doesntexistList.size()>0)
				json["Existing publisher names"] = doesntexistList
			json['results'] = response;
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		}catch (e) {
			log.error 'Failed to execute plugin', e
			message = e.getMessage()
			status = 500
		}
	}
}
public boolean validatePublisher(InternalArtifactoryContext  ctx,String userId){
	boolean isValidUser =  true
	def userDetails = new userdetails()
	
		def userInfo  = userDetails.getUserInfofromNtId(ctx, userId)
		if(userInfo.get("displayName")==null){
			isValidUser = false
		}
	
	return isValidUser
}

public int removePublisher(String username,String modulename){
	int response = 0
	def result = getModulePermission(modulename)
	String currentUser = security.getCurrentUsername()
	if(currentUser.equalsIgnoreCase(username)){
		throw new AccessDeniedException("User cannot delete self")
	}
	while(result.next()){
		if(currentUser.equalsIgnoreCase(result.getString("user_id"))){
			response = removePermission(modulename,username)
			break
		}
	}
	return response
}

public int addPublisher(String username,String modulename){
	int response = -1
	ResultSet result = getModulePermission(modulename)
	String currentUser = security.getCurrentUsername()
	boolean isAuthor = false
	boolean isPublisher = false
	while(result.next()){
		String userid = result.getString("user_id")
		if(currentUser.equalsIgnoreCase(userid))
			isAuthor = true

		if(username.equalsIgnoreCase(userid))
			isPublisher = true
	}
	if(isAuthor && !isPublisher){
		response = createPermission(modulename,username,currentUser)
		log.info("if : "+response)
	}else if(isAuthor && isPublisher){
		response = 0
		log.info("else if: "+response)
	}else{
		response = -1
		log.info("else : "+response)
	}
	return response
}

public void createPermissionForCurrentBuild(ItemInfo item){
	def repoPath =  item.getRepoPath()
	def repoKey =  item.getRepoKey()
	if(repoPath.isFile()) {
		def filePath = repoPath.path.toLowerCase()
		LocalRepositoryConfiguration repoConfig = repositories.getRepositoryConfiguration(repoPath.repoKey)
		if (ZipUtils.isZipFamilyArchive(filePath) || ZipUtils.isTarArchive(filePath) ||
		ZipUtils.isTgzFamilyArchive(filePath) || ZipUtils.isGzCompress(filePath)) {
			String artId = ""
			def key = item.repoKey
			FileLayoutInfo currentLayout = repositories.getLayoutInfo(repoPath)
			if(currentLayout.isValid()){
				def artifactInfo  = getMavenInfo(repoPath)
				artId = artifactInfo.getArtifactId()
			}
			if(repoConfig.getPackageType().equalsIgnoreCase("Npm")){
				def npmInfo = getNPMInfo(repoPath)
				artId = npmInfo.getName()
			}
			if(repoConfig.getPackageType().equalsIgnoreCase("NuGet")){
				def nugetInfo = getNugetInfo(repoPath)
				artId = nugetInfo.getId()
			}
			if(repoConfig.getPackageType().equalsIgnoreCase("Composer")){
				def composerInfo = getComposerInfo(repoPath)
				artId = composerInfo.getName()
			}
			validatePublish(artId)
		}
	}
}

private void validatePublish(String moduleName){
	def result = getModulePermission(moduleName)
	String currentUser = security.getCurrentUsername()
	boolean isAuthorized = false
	boolean isFirstPublish = true
	while(result.next()){
		isFirstPublish = false
		String username = result.getString("user_id")
		if(username.equalsIgnoreCase(currentUser)){
			isAuthorized = true
			break
		}
	}
	if(isFirstPublish){
		isAuthorized = true
		int success = createPermission(moduleName,currentUser,null)
		if(success==1)
			log.info("New permission created for the module")
	}
	if(!isAuthorized){
		log.info("user is not authorized to publish a latest module")
		throw new UnauthorizedException("User is not Authorized to do a publish")
	}
}

private int removePermission(String modulename,String username){
	return jdbcHelper.executeUpdate("DELETE FROM public.module_permissions WHERE module_name =? and user_id = ?", modulename,username)
}
private ResultSet getModulePermission(String moduleName){
	return jdbcHelper.executeSelect("SELECT module_name, user_id,authorized_by from public.module_permissions where module_name = ?", moduleName)
}

private int createPermission(String modulename,String user_id,String authorizedBy){
	return jdbcHelper.executeUpdate("INSERT INTO public.module_permissions(module_name, user_id,authorized_by,created)VALUES (?, ?, ?,?)",
			modulename,user_id,authorizedBy,System.currentTimeMillis())
}

public MavenArtifactInfo getMavenInfo(RepoPath repoPath){
	return MavenArtifactInfo.fromRepoPath(repoPath)
}

public NpmInfo getNPMInfo(RepoPath repoPath){
	AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class)
	NpmAddon npmAddon = addonsManager.addonByType(NpmAddon.class)
	NpmInfo npmInfo = null
	if (npmAddon != null) {
		// get npm meta data
		NpmMetadataInfo npmMetaDataInfo = npmAddon.getNpmMetaDataInfo(repoPath)
		npmInfo =  npmMetaDataInfo.getNpmInfo()
	}
	return npmInfo
}

public NuMetaData getNugetInfo(RepoPath repoPath){
	AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
	UiNuGetAddon uiNuGetAddon = addonsManager.addonByType(UiNuGetAddon.class)
	NuMetaData nugetSpecMetaData = null
	if (uiNuGetAddon != null) {
		nugetSpecMetaData = uiNuGetAddon.getNutSpecMetaData(repoPath);
		def id = nugetSpecMetaData.getId()
		log.debug("Nuget Id : "+id)
	}
	return nugetSpecMetaData
}
public ComposerInfo getComposerInfo(RepoPath repoPath){
	AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class)
	ComposerAddon composerAddon = addonsManager.addonByType(ComposerAddon.class)
	def composerInfo = null
	if(composerAddon != null ){
		ComposerMetadataInfo composerMetadataInfo =  composerAddon.getComposerMetadataInfo(repoPath)
		composerInfo = composerMetadataInfo.getComposerInfo()
	}
	return composerInfo
}