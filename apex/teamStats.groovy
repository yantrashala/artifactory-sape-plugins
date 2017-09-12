import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.storage.db.util.JdbcHelper
import org.artifactory.storage.db.util.DbUtils
import java.sql.ResultSetMetaData

executions {
	
	/*
	 * artifactory/api/plugins/execute/teamstats
	 * executes the closure if the request is from 'users' group
	 * 
	 */
	teamstats(httpMethod: 'GET', groups : 'users'){ params ->
	 
		def jdbcHelper = ctx.beanForType(JdbcHelper.class)
	
		def query = """select team,count(1) from ( select distinct A.prop_value as team, B.prop_value as name
		from public.node_props A, public.node_props B
		where A.node_id = B.node_id and
		A.node_id in (select distinct node_id from public.node_props) and
		A.prop_key = 'module.team' and
		B.prop_key = 'module.name'
		order by A.prop_value) as data
		group by team""";
		
		status = 200
		def str = getStats(jdbcHelper, query)
		message = new JsonBuilder(str).toPrettyString()
	}
	
	/*
	 * artifactory/api/plugins/execute/teammodulestats
	 * executes the closure if the request is from 'users' group
	 */
	teammodulestats(httpMethod: 'GET', groups : 'users'){ params ->
	 
		def jdbcHelper = ctx.beanForType(JdbcHelper.class)
	
		def query = """select A.prop_value as team, B.prop_value as moduleName, count(1)
		from public.node_props A, public.node_props B
		where 
		A.node_id = B.node_id and
		A.node_id in (select distinct node_id from public.node_props) and
		A.prop_key = 'module.team' and
		B.prop_key = 'module.name'
		group by A.prop_value, B.prop_value
		order by A.prop_value""";
		
		status = 200
		def str = getStats(jdbcHelper, query)
    	message = new JsonBuilder(str).toPrettyString() 
	}
	
	/*
	 * artifactory/api/plugins/execute/teammoduleversionstats
	 * executes the closure if the request is from 'users' group
	 */
	teammoduleversionstats(httpMethod: 'GET', groups : 'users'){ params ->
	 
		def jdbcHelper = ctx.beanForType(JdbcHelper.class)
	
		def query = """select A.node_id as node_id, A.prop_value as team, B.prop_value as name, C.prop_value as version 
		from public.node_props A, public.node_props B, public.node_props C
		where 
		A.node_id = B.node_id and
		A.node_id = C.node_id and
		B.node_id = C.node_id and
		A.node_id in (select distinct node_id from public.node_props) and
		A.prop_key = 'module.team' and
		B.prop_key = 'module.name' and
		C.prop_key in ('module.baseRevision', 'npm.version','composer.version','docker.label.version','nuget.version')
		order by A.prop_value, B.prop_value, C.prop_value
		""";
		
		status = 200
		def str = getStats(jdbcHelper, query)
    	message = new JsonBuilder(str).toPrettyString() 
	}
}

/*
 * parameters:
 * jdbcHelper - JdbcHelper object
 * query - postgresql query
 * returns - json with team statistics
 */
 def getStats(JdbcHelper jdbcHelper, query ) {
	def  rs = null, results = [];
	def json = [:]
	
	try {
	    
	    rs = jdbcHelper.executeSelect(query)
	    
	    if(rs) { 
	    
	    	ResultSetMetaData metadata = rs.getMetaData()
	    	def numColumns = metadata.getColumnCount()
	    	def row = [:]
	    	
		    while (rs.next()) {
		    	for(i=1 ; i <= numColumns ; i++) {
		    		def column = [:]
		    		column[metadata.getColumnName(i)] = rs.getObject(i)
		    		row += column
		    	}
		   		results += row
		    }
	    }
	} catch (ex) {
		results = []; 
	} finally {
		// close the result set
	    if (rs) DbUtils.close(rs)
	}
	json['results'] = results;
	return json
}