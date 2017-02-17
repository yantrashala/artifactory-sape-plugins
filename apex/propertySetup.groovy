import java.io.IOException


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
import org.artifactory.repo.InternalRepoPathFactory
import org.artifactory.repo.RepoPath
import org.artifactory.util.ZipUtils

import groovy.transform.Field


@Field final String PROPERTY_PREFIX = 'module.'

storage {
	afterCreate { ItemInfo item ->

		if(item.repoPath.isFile()) {

			RepositoryService repoService = ctx.beanForType(RepositoryService.class);

			def filePath = item.repoPath.path.toLowerCase()

			if (ZipUtils.isZipFamilyArchive(filePath) || ZipUtils.isTarArchive(filePath) ||
			ZipUtils.isTgzFamilyArchive(filePath) || ZipUtils.isGzCompress(filePath)) {

				try  {
					ArchiveInputStream archiveInputStream = repoService.archiveInputStream(item.repoPath)
					ArchiveEntry archiveEntry;
					while ((archiveEntry = archiveInputStream.getNextEntry()) != null) {

						if(archiveEntry.name.equalsIgnoreCase("readme.md")) {
							log.info("archiveEntry === ,archiveEntry      "+archiveEntry.name)
							
							
							def downloadPath = item.repoKey + "/" + item.repoPath.path + "!" + "/" + archiveEntry.name
							
							repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "readme", downloadPath as String)
							
						}

						if(archiveEntry.name.equalsIgnoreCase("apex.json")) {
							log.info("archiveEntry === ,archiveEntry       "+archiveEntry.name)
							def downloadPath = item.repoKey + "/" + item.repoPath.path + "!" + "/" + archiveEntry.name
							repositories.setProperty(item.repoPath, PROPERTY_PREFIX + "apex", downloadPath as String)
						}

					}
				} catch (IOException e) {
					log.error("Failed to get  zip Input Stream: " + e.getMessage());
				}
			}
		}
	}
}
