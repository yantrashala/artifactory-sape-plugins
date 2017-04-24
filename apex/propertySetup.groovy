import java.io.IOException
import java.util.logging.Logger

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.artifactory.api.context.ContextHelper
import org.artifactory.api.repo.RepositoryService
import org.artifactory.factory.InfoFactoryHolder
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.model.xstream.fs.FileInfoImpl
import org.artifactory.repo.InternalRepoPathFactory
import org.artifactory.repo.RepoPath
import org.artifactory.repo.Repositories
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.util.ZipUtils
import org.artifactory.repo.LocalRepositoryConfiguration

import groovy.json.JsonSlurper
import groovy.transform.Field


@Field final String PROPERTY_PREFIX = 'module.'
@Field final String NAME = 'name'
@Field final String IMAGE = 'image'
@Field final String BASEREVISION = 'baseRevision'
@Field final String README = 'readme.md'
@Field final String APEX = 'appx.json'
@Field final String SNAPSHOT = 'SNAPSHOT'
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
					def properties  = repositories.getProperties(repoPath)

					// Setting the properties for the default tokens
					def id = ""
					ArchiveInputStream archiveInputStream1 = repoService.archiveInputStream(item.repoPath)
					ArchiveEntry archiveEntry1;
					LocalRepositoryConfiguration repoConfig = repositories.getRepositoryConfiguration(repoPath.repoKey)
					['organization', 'name', 'baseRevision', 'ext', 'image'].each { String propName ->
						if(propName.equals(NAME)){
							if (currentLayout.isValid()){
								id = currentLayout.module
							}
							if (repoConfig.getPackageType().equalsIgnoreCase("Npm") ||
								repoConfig.getPackageType().equalsIgnoreCase("Composer")){
								log.debug("package type : "+repoConfig.getPackageType())
								while((archiveEntry1 = archiveInputStream1.getNextEntry()) != null){
									if(archiveEntry1.name.toLowerCase().endsWith("package.json")||
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
							}

							repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, id as String)
						}
						else if(propName.equals(IMAGE)) {
							repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, "/artifactory/assets/images/common/noImage.jpg" as String)
						}

						else if(propName.equals(BASEREVISION)) {
							def mavenVersion = '';
							if(currentLayout.folderIntegrationRevision.equals(SNAPSHOT))
								mavenVersion = currentLayout."$propName" + '-' + SNAPSHOT
							else mavenVersion = currentLayout."$propName"
							repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, mavenVersion as String)
						}

						else
							repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, currentLayout."$propName" as String)
					} // This pulls all the default tokens

					ArchiveInputStream archiveInputStream = repoService.archiveInputStream(item.repoPath)
					ArchiveEntry archiveEntry;

					// Below two length flags are to get the shortest path for readme and apex file
					def readmeLength = 0
					def apexLength = 0
					while ((archiveEntry = archiveInputStream.getNextEntry()) != null) {

						if(archiveEntry.name.toLowerCase().endsWith(README)){
							if(readmeLength == 0 || readmeLength > archiveEntry.name.length()) {
								readmeLength = archiveEntry.name.length()

								def downloadPath = item.repoKey + "/" + item.repoPath.path + "!" + "/" + archiveEntry.name
								repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "readme", downloadPath as String)
							}
						}
						else if(archiveEntry.name.toLowerCase().endsWith(APEX) ) {
							if(apexLength == 0 || apexLength > archiveEntry.name.length()) {
								apexLength = archiveEntry.name.length()

								def downloadPath = item.repoKey + "/" + item.repoPath.path + "!" + "/" + archiveEntry.name
								repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "appx", downloadPath as String)

								// Adding properties from apex.json file
								def str = repoService.getGenericArchiveFileContent(repoPath, archiveEntry.name).getContent()
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
										repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "type", type as String)
									} else
										repositories.setProperty(item.repoPath, PROPERTY_PREFIX + it.key, it.value.toLowerCase() as String)
								}
							}
						}
					}
				} catch (IOException e) {
					log.error("Failed to get  zip Input Stream: " + e.getMessage());
				}
			}
		}
	}
}
