import org.artifactory.api.mail.MailService
import org.artifactory.api.security.UserGroupService
import org.artifactory.descriptor.config.CentralConfigDescriptor
import org.artifactory.descriptor.mail.MailServerDescriptor
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.repo.RepoPath
import org.artifactory.security.UserInfo
import org.artifactory.spring.InternalArtifactoryContext
import org.artifactory.util.EmailException
import org.artifactory.util.ZipUtils
import org.artifactory.api.context.ContextHelper
import org.artifactory.addon.composer.ComposerInfo
import org.artifactory.nuget.NuMetaData
import org.artifactory.addon.npm.NpmInfo
import org.artifactory.api.maven.MavenArtifactInfo
import org.artifactory.api.config.CentralConfigService
import ac_1_propertySetup
import org.artifactory.mime.MavenNaming

import groovy.transform.Field

@Field final String POWER_USERS_GROUP = 'power_users'

storage {
	afterCreate {  ItemInfo item ->
		if(item.repoPath.isFile()) {
			RepoPath repoPath = item.repoPath
			def filePath = item.repoPath.path.toLowerCase()
			FileLayoutInfo currentLayout = repositories.getLayoutInfo(repoPath)
			LocalRepositoryConfiguration repoConfig = repositories.getRepositoryConfiguration(repoPath.repoKey)
			if (ZipUtils.isZipFamilyArchive(filePath) || ZipUtils.isTarArchive(filePath) ||
			ZipUtils.isTgzFamilyArchive(filePath) || ZipUtils.isGzCompress(filePath)) {
				def propertySetup = new ac_1_propertySetup()
				def moduleName = ""
				def version = ""
				String currentUserEmail  = security.currentUser().getEmail()
				if(repoConfig.getPackageType().equalsIgnoreCase("Maven")){
					def artifactInfo  = propertySetup.getMavenInfo(repoPath)
					if(!artifactInfo.hasClassifier())
						moduleName = artifactInfo.getArtifactId()
					if(MavenNaming.isSnapshot(repoPath.getPath())){
						version = artifactInfo.getVersion().split("-")[0]+MavenNaming.SNAPSHOT_SUFFIX
					}else{
						version = artifactInfo.getVersion()
					}
				}
				else if(repoConfig.getPackageType().equalsIgnoreCase("Npm")){
					def npmInfo = propertySetup.getNPMInfo(repoPath)
					moduleName = npmInfo.getName()
					version = npmInfo.getVersion()
				}
				else if(repoConfig.getPackageType().equalsIgnoreCase("NuGet")){
					def nugetInfo = propertySetup.getNugetInfo(repoPath)
					moduleName = nugetInfo.getId()
					version = nugetInfo.getVersion()
				}
				else if(repoConfig.getPackageType().equalsIgnoreCase("Composer")){
					def composerInfo = propertySetup.getComposerInfo(repoPath)
					moduleName = composerInfo.getName()
					version = composerInfo.getVersion()
				}
				else if(repoConfig.getPackageType().equalsIgnoreCase("Generic")){
					moduleName = (item.name =~ '^(?:\\D[^.]*\\-)')[0] - ~'\\-$'
					version = (item.name =~ '(?:\\d{1,}\\.\\d{1,}\\.\\d{1,})')[-1]
				}


				if(!moduleName.isEmpty() && !version.isEmpty()){
					trySendMail(moduleName,version,currentUserEmail)

				}
			}
		}
	}
}



def trySendMail(String moduleName,String version,String currenUser) {
	MailService mailService = ContextHelper.get().beanForType(MailService)
	try {
		String sub = "Module Published - "+moduleName
		def recipientsList  = findAdminEmails()
		recipientsList.add(currenUser)
		String[] recipients  = recipientsList.toArray()
		log.debug("recipients : "+recipients)
		mailService.sendMail(recipients, sub, getEmailBody(moduleName, version))
	} catch (EmailException e) {
		log.error("Error while sending storage quota mail message.", e)
	}
}


List findAdminEmails() {
	UserGroupService userGroupService = ContextHelper.get().beanForType(UserGroupService)
	userGroupService.findUsersInGroup(POWER_USERS_GROUP).findAll(){ UserInfo userInfo -> userInfo.email }.collect { it.email}

}
String getEmailBody(String moduleName,String version){
	String message = ""
	try{
		message = new File("/etc/opt/jfrog/artifactory/Thankyou.htm").getText()
	}catch(FileNotFoundException e){
		e.printStackTrace()
	}
	message = message.replaceAll("###MODULE_NAME###", moduleName)
	def moduleDetailsUrl = getArtifactoryUrl()+"/module/"+moduleName+"/"+version
	message = message.replaceAll("###MODULE_URL###", moduleDetailsUrl)
	return message
}
String getArtifactoryUrl(){
	String artUrl = ""
	CentralConfigService centralConfigService = ContextHelper.get().beanForType(CentralConfigService)
	CentralConfigDescriptor descriptor = centralConfigService.getDescriptor()
	MailServerDescriptor mailServerDescriptor = descriptor.getMailServer()
	if(mailServerDescriptor != null)
		artUrl = mailServerDescriptor.getArtifactoryUrl()

	return artUrl
}
