import java.io.IOException

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.artifactory.api.context.ContextHelper
import org.artifactory.api.maven.MavenArtifactInfo
import org.artifactory.api.repo.RepositoryService
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.Repositories
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.composer.ComposerInfo
import org.artifactory.addon.npm.NpmAddon;
import org.artifactory.addon.npm.NpmInfo
import org.artifactory.addon.npm.NpmMetadataInfo;
import org.artifactory.addon.nuget.UiNuGetAddon
import org.artifactory.nuget.NuMetaData
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.util.ZipUtils
import org.artifactory.addon.composer.ComposerAddon
import org.artifactory.addon.composer.ComposerMetadataInfo
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.Field

@Field final String PROPERTY_PREFIX = 'module.'
@Field final String NAME = 'name'
@Field final String IMAGE = 'image'
@Field final String BASEREVISION = 'baseRevision'
@Field final String README = 'readme.md'
@Field final String APEX = 'appx.json'
@Field final String SNAPSHOT = 'SNAPSHOT'
@Field final String IMAGEPATH = '/artifactory/assets/images/common/noImage.jpg'

storage {
	afterCreate { ItemInfo item ->

		if(item.repoPath.isFile()) {
			RepoPath repoPath = item.repoPath
			RepositoryService repoService = ctx.beanForType(RepositoryService.class);
			FileLayoutInfo currentLayout = repositories.getLayoutInfo(repoPath)
			def filePath = item.repoPath.path.toLowerCase()

			if (ZipUtils.isZipFamilyArchive(filePath) || ZipUtils.isTarArchive(filePath) ||
			ZipUtils.isTgzFamilyArchive(filePath) || ZipUtils.isGzCompress(filePath)) {

				try  {

					// Setting the properties for the default tokens
					def id = ""
					LocalRepositoryConfiguration repoConfig = repositories.getRepositoryConfiguration(repoPath.repoKey)
					['organization', 'name', 'baseRevision', 'ext', 'image'].each { String propName ->
						if(propName.equals(NAME)){
							if(currentLayout.isValid()){
								def artifactInfo  = getMavenInfo(repoPath)
								if(!artifactInfo.hasClassifier())
									id = artifactInfo.getArtifactId()
								
							}
							if(repoConfig.getPackageType().equalsIgnoreCase("Npm")){
								def npmInfo = getNPMInfo(repoPath)
								id = npmInfo.getName()
							}
							if(repoConfig.getPackageType().equalsIgnoreCase("NuGet")){
								def nugetInfo = getNugetInfo(repoPath)
								id = nugetInfo.getId()
							}
							if(repoConfig.getPackageType().equalsIgnoreCase("Composer")){
								def composerInfo = getComposerInfo(repoPath)
								id = composerInfo.getName()
							}
							if(!id.isEmpty())
								repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, id as String)
							repositories.setProperty(repoPath,"module.approved",false as String)
						}
						else if(propName.equals(IMAGE))
							repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, getImagePath(id) as String)
						else if(propName.equals(BASEREVISION))
							repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, getMavenVersion(currentLayout, propName) as String)
						else
							repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, currentLayout."$propName" as String)

					} // This pulls all the default tokens

					// To set description for NPM modules
					if (repoConfig.getPackageType().equalsIgnoreCase("Npm")){
						setNpmDescription(repoPath)
					}

					ArchiveInputStream archiveInputStream = repoService.archiveInputStream(item.repoPath)
					ArchiveEntry archiveEntry;

					// Below two length flags are to get the shortest path for readme and apex file
					def readmeLength = 0
					def apexLength = 0
					while ((archiveEntry = archiveInputStream.getNextEntry()) != null) {
						if(archiveEntry.name.toLowerCase().endsWith(README)){
							if(readmeLength == 0 || readmeLength > archiveEntry.name.length()) {
								readmeLength = archiveEntry.name.length()

								def downloadPath = getDownloadPath(item.repoKey, item.repoPath.path, archiveEntry.name)
								repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "readme", downloadPath  as String)
							}
						}
						else if(archiveEntry.name.toLowerCase().endsWith(APEX) ) {
							if(apexLength == 0 || apexLength > archiveEntry.name.length()) {
								apexLength = archiveEntry.name.length()
								def downloadPath = getDownloadPath(item.repoKey, item.repoPath.path, archiveEntry.name)
								repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "appx", downloadPath  as String)

								// Adding properties from apex.json file
								def str = repoService.getGenericArchiveFileContent(repoPath, archiveEntry.name).getContent()
								setAppxProperties(str, item.repoPath, id)
							}
						} else if(archiveEntry.name.toLowerCase().endsWith("pom.xml")){
							// To setup description for Maven application
							def str = repoService.getGenericArchiveFileContent(repoPath, archiveEntry.name).getContent()
							def pom= new XmlParser().parseText(str)
							repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "description", pom.description.text() as String)
						}
					}
				} catch (IOException e) {
					log.error("Failed to get  zip Input Stream: " + e.getMessage());
				}
			} else if (filePath.endsWith("manifest.json")) {
				// Adding Image property for dockers manifest.json file by default
				repositories.setProperty(repoPath, PROPERTY_PREFIX + "image", IMAGEPATH as String)
				// Setting the module.readme property for dockers.
				// Setting the readme path same as manifest.json for a docker repository
				def readmePath = "dockers/"+ filePath.replaceAll("manifest.json", "readme.md")
				repositories.setProperty(repoPath, PROPERTY_PREFIX + "readme", readmePath as String)
			}
		}
	}
}

private String getModuleName(repoService, repoPath) {
	def id = ""
	ArchiveInputStream archiveInputStream1 = repoService.archiveInputStream(repoPath)
	ArchiveEntry archiveEntry1;

	while((archiveEntry1 = archiveInputStream1.getNextEntry()) != null){
		if(archiveEntry1.name.toLowerCase().equals("package/package.json")||
		archiveEntry1.name.toLowerCase().endsWith("composer.json")){
			def str = repoService.getGenericArchiveFileContent(repoPath, archiveEntry1.name).getContent()
			def json = new JsonSlurper().parse(str.toCharArray())
			def list = new JsonSlurper().parseText( str )
			list.each {
				if(it.key.equalsIgnoreCase("name")){
					id = it.value
				}
			}
		}
	}
	return id
}

private String getMavenVersion(currentLayout, propName) {
	if(currentLayout.folderIntegrationRevision.equals(SNAPSHOT))
		return currentLayout."$propName" + '-' + SNAPSHOT
	else
		return currentLayout."$propName"
}

private void setNpmDescription(repoPath) {
	AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class)
	NpmAddon npmAddon = addonsManager.addonByType(NpmAddon.class)
	if (npmAddon != null) {
		// get npm meta data
		NpmMetadataInfo npmMetaDataInfo = npmAddon.getNpmMetaDataInfo(repoPath)
		repositories.setProperty(repoPath, "npm.description", npmMetaDataInfo.getNpmInfo().description as String)
	}
}

private String getDownloadPath(repoKey, path , name) {
	return repoKey + "/" + path + "!" + "/" + name
}

private void setAppxProperties(str, repoPath, id ) {

	def json = new JsonSlurper().parse(str.toCharArray())

	// Parse the response
	def list = new JsonSlurper().parseText( str )

	// Print them out to make sure
	list.each {
		if(it.key.equalsIgnoreCase("distribution")){
			repositories.setProperty(repoPath, PROPERTY_PREFIX + "distribution", it.value as String)
		} else if(it.key.equalsIgnoreCase("keywords")) {
			def keywordList = it.value.toLowerCase() + "," + id.toLowerCase()
			repositories.setProperty(repoPath, PROPERTY_PREFIX + it.key , keywordList as String)
		} else
			repositories.setProperty(repoPath, PROPERTY_PREFIX + it.key, it.value.toLowerCase() as String)
	}
}


private String getImagePath(id) {

	def names = [ ['@module.name': ['$match': id]], ['@docker.label.team': ['$match': id]] ]
	def query = ['$or': names]
	def aql = "items.find(${new JsonBuilder(query).toString()})" +
			".include(\"*\")"
	log.info('----aql----'+aql)
	def flag = false
	def imagePath = ''
	try {
		def aqlserv = ctx.beanForType(AqlService)
		def queryresults = aqlserv.executeQueryEager(aql).results

		if(queryresults.size() > 0) {
			flag = true
			AqlBaseFullRowImpl aqlresult = queryresults.get(0)
			path = "$aqlresult.path/$aqlresult.name"
			rpath = RepoPathFactory.create(aqlresult.repo, path)
	                def properties  = repositories.getProperties(rpath)
			imagePath = properties.get("module.image").getAt(0)
		}
	} catch (Exception e) {
		log.error("Failed in getImagePath method: " + e.getMessage());
	}

	if(flag)
		return imagePath
	else
		return IMAGEPATH
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