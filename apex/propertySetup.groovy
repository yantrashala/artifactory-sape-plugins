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

import groovy.json.JsonSlurper
import groovy.transform.Field


@Field final String PROPERTY_PREFIX = 'module.'

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
					// Setting the properties for the default token
					def id = ""
					['organization', 'name', 'baseRevision', 'ext', 'image'].each { String propName ->
	                	if(propName.equals("name")){
	                		if (currentLayout.isValid()) id = currentLayout.module
	                		else id = (item.name =~ '^(?:\\D[^.]*\\-)+')[0] - ~'\\-$' 
	                		repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, id as String)
	                	}
	                	else if(propName.equals("image")) 
	                		repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, "/artifactory/content/artworks/" + id + "/" + id + ".jpg" as String)	
	                	else
	                		repositories.setProperty(repoPath, PROPERTY_PREFIX + propName, currentLayout."$propName" as String)
	                } // This pulls all the default tokens

					ArchiveInputStream archiveInputStream = repoService.archiveInputStream(item.repoPath)
					ArchiveEntry archiveEntry;
					while ((archiveEntry = archiveInputStream.getNextEntry()) != null) {
						log.info('----------------'+archiveEntry.name)
						//if(archiveEntry.name.equalsIgnoreCase("readme.md")) {
						if(archiveEntry.name.toLowerCase().contains("readme.md")){
							log.info("Inside Readme condition :: "+archiveEntry.name)
							def downloadPath = item.repoKey + "/" + item.repoPath.path + "!" + "/" + archiveEntry.name
							repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "readme", downloadPath as String)
						}
						else if(archiveEntry.name.equalsIgnoreCase("apex.json")) {
							log.info("Inside Apex condition :: "+archiveEntry.name)
							def downloadPath = item.repoKey + "/" + item.repoPath.path + "!" + "/" + archiveEntry.name
							repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "apex", downloadPath as String)
							
							// Adding properties from apex.json file
							def str = repoService.getGenericArchiveFileContent(repoPath, archiveEntry.name).getContent()
							def json = new JsonSlurper().parse(str.toCharArray())
							
							// Parse the response
							def list = new JsonSlurper().parseText( str )

							// Print them out to make sure
							list.each { 
								repositories.setProperty(item.repoPath, PROPERTY_PREFIX + it.key, it.value as String)
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
