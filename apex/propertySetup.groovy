import java.io.IOException

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.artifactory.api.context.ContextHelper
import org.artifactory.api.repo.RepositoryService
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.Repositories
import org.artifactory.util.ZipUtils
import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.npm.NpmAddon;
import org.artifactory.addon.npm.NpmMetadataInfo;
import org.artifactory.addon.nuget.UiNuGetAddon
import org.artifactory.nuget.NuMetaData

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
					//ArchiveInputStream archiveInputStream1 = repoService.archiveInputStream(item.repoPath)
					//ArchiveEntry archiveEntry1;
					LocalRepositoryConfiguration repoConfig = repositories.getRepositoryConfiguration(repoPath.repoKey)
					['organization', 'name', 'baseRevision', 'ext', 'image'].each { String propName ->
						if(propName.equals(NAME)){
							if (currentLayout.isValid()){
								id = currentLayout.module
							}
							else if (repoConfig.getPackageType().equalsIgnoreCase("Npm") ||
								repoConfig.getPackageType().equalsIgnoreCase("Composer")){
								log.debug("package type : "+repoConfig.getPackageType())
								id = getModuleName(repoService, repoPath)
							}
							else if(repoConfig.getPackageType().equalsIgnoreCase("NuGet")){
								id = getNugetModuleName(repoService, repoPath)
							}
							repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, id as String)
						}
						else if(propName.equals(IMAGE))
							repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, IMAGEPATH as String)
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
								setAppxProperties(str, item.repoPath)
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

private String getNugetModuleName(repoService,repoPath) {
	def id = ""
	ArchiveInputStream archiveInputStream1 = repoService.archiveInputStream(repoPath)
	ArchiveEntry archiveEntry1;

	while((archiveEntry1 = archiveInputStream1.getNextEntry()) != null){
		if(archiveEntry1.name.toLowerCase().endsWith("nuspec")){
			AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
			UiNuGetAddon uiNuGetAddon = addonsManager.addonByType(UiNuGetAddon.class);
			if (uiNuGetAddon != null) {
				NuMetaData nugetSpecMetaData = uiNuGetAddon.getNutSpecMetaData(repoPath);
				id = nugetSpecMetaData.getId()
				log.debug("Nuget Id : "+id)

			}
		}

	}
	return id;
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

private void setAppxProperties(str, repoPath) {

	def json = new JsonSlurper().parse(str.toCharArray())

	// Parse the response
	def list = new JsonSlurper().parseText( str )

	// Print them out to make sure
	list.each {
		if(it.key.equalsIgnoreCase("distribution")){
			def type
			if(!it.value)
				type = 'artifact'
			else
				type = 'distribution'
			repositories.setProperty(repoPath, PROPERTY_PREFIX + "type", type as String)
		} else
			repositories.setProperty(repoPath, PROPERTY_PREFIX + it.key, it.value.toLowerCase() as String)
	}
}
