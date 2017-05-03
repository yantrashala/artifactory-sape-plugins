import groovy.json.JsonBuilder
import java.time.chrono.AbstractChronology
import org.artifactory.aql.AqlService
import org.artifactory.aql.result.AqlEagerResult
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl
import org.artifactory.aql.result.rows.AqlRowResult
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.npm.NpmAddon;
import org.artifactory.addon.npm.NpmMetadataInfo;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo;

executions{
	latestmodules(httpMethod: 'GET', groups : 'users'){ params ->

		try {
			// getting keyword as url parameters
			def aqlserv = ctx.beanForType(AqlService)
			def obj = null
			def sub = ['$desc':["created"]]
			// AQL query to get the module details
			def query = ["@module.name":"*"]
			//def aql = "items.find({\"@module.name\":{\"\$ne\":\"$obj\"}}).sort(${new JsonBuilder(sub).toString()}).limit(10)"
			def aql = "items.find(${new JsonBuilder(query).toString()}).sort(${new JsonBuilder(sub).toString()}).limit(12)"
			log.info('---------------AQL---------'+aql)

			def queryresults = aqlserv.executeQueryEager(aql).results
			log.info("result set size  "+queryresults.size())
			def list = []

			for (AqlBaseFullRowImpl var in queryresults) {
				log.info("this is reppo path : "+var.path)
				path = "$var.path/$var.name"
				rpath = RepoPathFactory.create(var.repo, path)

				def properties  = repositories.getProperties(rpath)
				log.info("this is reppo path : "+var.path)
				log.info("these are properties : "+properties)
				if(!properties.isEmpty() /*&&  properties.get("module.name").getAt(0) != null && !properties.get("module.name").getAt(0).isEmpty() */){
					def details = [:]
					details['name'] =  properties.get("module.name").getAt(0)
					details['version'] = properties.get("npm.version").getAt(0)?: properties.get("composer.version").getAt(0) ?: properties.get("module.baseRevision").getAt(0) ?: "N/A"
					details['image'] = properties.get("module.image").getAt(0) ?: "N/A"
					//details['readme'] =  properties.get("module.readme").getAt(0) ?: "N/A"
					//details['gatekeepers'] = properties.get("module.gatekeepers").getAt(0) ?: "N/A"
					//details['keywords']= properties.get("module.keywords").getAt(0) ?: "N/A"
					details['team']= properties.get("module.team").getAt(0) ?: "N/A"
					//details['type']= properties.get("module.type").getAt(0) ?: "N/A"
					//details['created'] = var.created.getTime()
					//details['path'] = rpath.getPath()
					//details['nameforVar'] = var.getName()
					details['description'] = properties.get("npm.description").getAt(0) ?: properties.get("composer.description").getAt(0) ?: "N/A"
					if(details['description'] == "N/A")
					{
						details['description'] = ""
						LocalRepositoryConfiguration repoConfig = repositories.getRepositoryConfiguration(rpath.repoKey)
						if(repoConfig.getPackageType().equalsIgnoreCase("Npm"))
						{
							AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class)
							RepositoryService repositoryService = ctx.beanForType(RepositoryService.class);
							NpmAddon npmAddon = addonsManager.addonByType(NpmAddon.class)
							if (npmAddon != null) {
								// get npm meta data
								ItemInfo itemInfo = repositoryService.getItemInfo(rpath)
								NpmMetadataInfo npmMetaDataInfo = npmAddon.getNpmMetaDataInfo(rpath)
								def desc = npmMetaDataInfo.getNpmInfo().description
								details['description'] = desc
								repositories.setProperty(rpath, "npm.description", desc as String)
							}
						}
					}
					list.add(details)
				}
			}

			message = new JsonBuilder(list).toPrettyString()
			status = 200
		} catch (e) {
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
}
