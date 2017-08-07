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
import org.artifactory.mime.MavenNaming
import org.artifactory.nuget.NuMetaData
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.util.ZipUtils
import org.artifactory.addon.composer.ComposerAddon
import org.artifactory.addon.composer.ComposerMetadataInfo
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.Field
import artifactoryutil


@Field final String PROPERTY_PREFIX = 'module.'
@Field final String NAME = 'name'
@Field final String IMAGE = 'image'
@Field final String BASEREVISION = 'baseRevision'
@Field final String README = 'readme.md'
@Field final String APEX = 'appx.json'
@Field final String SNAPSHOT = 'SNAPSHOT'
@Field final String IMAGEPATH = '/artifactory/assets/images/common/noImage.jpg'
@Field Map ART_PROPERTIES = [:]

storage {
	afterCreate { ItemInfo item ->

		if(item.repoPath.isFile()) {
			RepoPath repoPath = item.repoPath
			RepositoryService repoService = ctx.beanForType(RepositoryService.class);
			FileLayoutInfo currentLayout = repositories.getLayoutInfo(repoPath)
			def filePath = item.repoPath.path.toLowerCase()
			def artUtil  = new artifactoryutil()
			if (ZipUtils.isZipFamilyArchive(filePath) || ZipUtils.isTarArchive(filePath) ||
			ZipUtils.isTgzFamilyArchive(filePath) || ZipUtils.isGzCompress(filePath)) {

				try  {
					LocalRepositoryConfiguration repoConfig = repositories.getRepositoryConfiguration(repoPath.repoKey)
					Map artProp = artUtil.getModuleName(repoConfig,repoPath)
					ART_PROPERTIES.putAll(artProp)
					ART_PROPERTIES.put("module.approved",false as String)
					ART_PROPERTIES.put(PROPERTY_PREFIX + "image", getImagePath(ART_PROPERTIES.get("module.name")) as String)
					if (repoConfig.getPackageType().equalsIgnoreCase("Npm")){
						setNpmDescription(repoPath)
					}
					setResourcesPath(repoService, item)
				} catch (IOException e) {
					log.error("Failed to get  zip Input Stream: " + e.getMessage());
				}
			} else if (filePath.endsWith("manifest.json")) {
				ART_PROPERTIES.put(PROPERTY_PREFIX + "image", IMAGEPATH as String)
				def readmePath = "dockers/"+ filePath.replaceAll("manifest.json", "readme.md")
				ART_PROPERTIES.put(PROPERTY_PREFIX + "readme", readmePath as String)
			}
			if(ART_PROPERTIES.get("module.name")!=""){
				setArtifactProperties(repositories,repoPath)
			}
		}
	}
}

private void setNpmDescription(repoPath) {
	AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class)
	NpmAddon npmAddon = addonsManager.addonByType(NpmAddon.class)
	if (npmAddon != null) {
		NpmMetadataInfo npmMetaDataInfo = npmAddon.getNpmMetaDataInfo(repoPath)
		ART_PROPERTIES.put("npm.description", npmMetaDataInfo.getNpmInfo().description as String)
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
			ART_PROPERTIES.put(PROPERTY_PREFIX + "distribution", it.value as String)
		} else if(it.key.equalsIgnoreCase("keywords")) {
			def keywordList = it.value.toLowerCase() + "," + id.toLowerCase()
			ART_PROPERTIES.put(PROPERTY_PREFIX + it.key , keywordList as String)
		} else
			ART_PROPERTIES.put( PROPERTY_PREFIX + it.key, it.value.toLowerCase() as String)
	}
}

private void setResourcesPath(RepositoryService repoService,ItemInfo item){
	ArchiveInputStream archiveInputStream = repoService.archiveInputStream(item.getRepoPath())
	ArchiveEntry archiveEntry;
	def readmeLength = 0
	def apexLength = 0
	while ((archiveEntry = archiveInputStream.getNextEntry()) != null) {
		if(archiveEntry.name.toLowerCase().endsWith(README)){
			if(readmeLength == 0 || readmeLength > archiveEntry.name.length()) {
				readmeLength = archiveEntry.name.length()
				def downloadPath = getDownloadPath(item.repoKey, item.repoPath.path, archiveEntry.name)
				ART_PROPERTIES.put(PROPERTY_PREFIX + "readme", downloadPath  as String)
			}
		}
		else if(archiveEntry.name.toLowerCase().endsWith(APEX) ) {
			if(apexLength == 0 || apexLength > archiveEntry.name.length()) {
				apexLength = archiveEntry.name.length()
				def downloadPath = getDownloadPath(item.repoKey, item.repoPath.path, archiveEntry.name)
				ART_PROPERTIES.put(PROPERTY_PREFIX + "appx", downloadPath  as String)
				def str = repoService.getGenericArchiveFileContent(item.repoPath, archiveEntry.name).getContent()
				setAppxProperties(str, item.repoPath, ART_PROPERTIES.get("module.name") as String)
			}
		} else if(archiveEntry.name.toLowerCase().endsWith("pom.xml")){
			def str = repoService.getGenericArchiveFileContent(item.repoPath, archiveEntry.name).getContent()
			def pom= new XmlParser().parseText(str)
			ART_PROPERTIES.put( PROPERTY_PREFIX + "description", pom.description.text() as String)
		}
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

private void setArtifactProperties(Repositories repositories,RepoPath repoPath){
	for(Map.Entry<String,String> entry : ART_PROPERTIES.entrySet()){
		repositories.setProperty(repoPath,entry.getKey() as String ,entry.getValue() as String)
	}
	ART_PROPERTIES.clear()
}
