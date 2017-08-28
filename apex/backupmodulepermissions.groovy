import java.sql.ResultSet
import org.artifactory.storage.db.util.DbUtils
import org.artifactory.storage.db.util.JdbcHelper
import groovy.transform.Field
import au.com.bytecode.opencsv.CSVReader
import groovy.json.JsonBuilder
import groovy.sql.Sql
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Field JdbcHelper jdbcHelper = ctx.beanForType(JdbcHelper.class)

jobs {
	backUpModule(cron: "0 0 0/24 * * ?"){ takeBackup() }
}
executions{
	backupmodulepermission(httpMethod: 'GET', groups : 'power_users'){ params ->
		try{
			takeBackup()
			def json = [:]
			json["status"] = "Successfully Exported Module Permissions"
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		}catch(e){
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
	importmodulepermission(httpMethod: 'POST', groups : 'power_users'){ params ->
		try{
			def fileName = params?.get('filename').getAt(0)
			importMoudlePermission(fileName)
			def json = [:]
			json["status"] = "Successfully Imported Module Permissions"
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		}catch(e){
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
	setupdb(httpMethod: 'GET', groups : 'power_users'){ params ->

		try{
			dropExistingSetup()
			setupDB()
			def json = [:]
			json["status"] = 'Module Permissions setup completed Successfully'
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		}catch(e){
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin '+e
			status = 500
		}
	}
}
public void takeBackup(){
	ResultSet resultSet  = getModulePermission()
	String fileName = "Module_Permissions_"+LocalDateTime.now()+"_Backup"
	File f = new File("/etc/opt/jfrog/artifactory/"+fileName+".csv")
	while(resultSet.next()){
		f.append("\n"+resultSet.getString(1)+";"+resultSet.getString(2)+";"+resultSet.getString(3)+";"+resultSet.getString(4)+";"+resultSet.getString(5))
	}
	DbUtils.close(resultSet)
	zipfile(f)
}

public void zipfile(File csvFile){
	String fileName = "Module_Permissions_"+LocalDateTime.now()+"_Backup"
	ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream("/etc/opt/jfrog/artifactory/"+fileName+".zip"))
	if (csvFile.isFile()){
		zipFile.putNextEntry(new ZipEntry(csvFile.name))
		def buffer = new byte[csvFile.size()]
		csvFile.withInputStream {
			zipFile.write(buffer, 0, it.read(buffer))
		}
		zipFile.closeEntry()
	}
	csvFile.delete()
	zipFile.close()
}

public void importMoudlePermission(String fileName){
	def zip = new ZipFile(new File("/etc/opt/jfrog/artifactory/"+fileName))
	zip.entries().each{
		if (!it.isDirectory()){
			def fOut = new File("/etc/opt/jfrog/artifactory/"+ File.separator + it.name)
			new File(fOut.parent).mkdirs()
			def fos = new FileOutputStream(fOut)
			def buf = new byte[it.size]
			def len = zip.getInputStream(it).read(buf) //println zip.getInputStream(it).text
			fos.write(buf, 0, len)
			def reader = new CSVReader(new FileReader(new File(fOut.toString())))
			reader.readNext()
			def header = reader.readAll()
			reader.readNext()
			header.each {String[] csvEntries ->
				String[] entry = csvEntries[0].split(";")
				String name = entry[1]
				String user = entry[2]
				String author = entry[3]
				Long timestamp = Long.parseLong(entry[4])
				createPermission(name,user,author,timestamp)
				
			}
			fos.close()
			fOut.delete()
		}
	}
	zip.close()
}
private ResultSet getModulePermission(){
	return jdbcHelper.executeSelect("SELECT * from public.module_permissions")
}

private void createPermission(String modulename,String user_id,String authorizedBy,Long currentTime){

	jdbcHelper.executeUpdate("INSERT INTO public.module_permissions(module_name, user_id,authorized_by,created)VALUES (?, ?, ?, ?)",
			modulename,user_id,authorizedBy,currentTime)

}
private void setupDB(){

	jdbcHelper.executeUpdate("CREATE SEQUENCE public.module_permissions_permission_id_seq "+
			"INCREMENT 1 START 211 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1")

	jdbcHelper.executeUpdate("CREATE TABLE public.module_permissions_lat "+
			"(permission_id integer NOT NULL DEFAULT nextval('module_permissions_permission_id_seq'::regclass),"+
			"module_name character varying(2048) COLLATE pg_catalog.default NOT NULL,"+
			"user_id character varying(64) COLLATE pg_catalog.default NOT NULL,"+
			"authorized_by character varying(64) COLLATE pg_catalog.default,"+
			"created bigint NOT NULL,CONSTRAINT module_permissions_pkey PRIMARY KEY (permission_id)"+
			")WITH (OIDS = FALSE)TABLESPACE pg_default")

	jdbcHelper.executeUpdate("CREATE INDEX module_permissions_module_name_idx "+
			"ON public.module_permissions USING btree "+
			"(module_name COLLATE pg_catalog.default) "+
			"TABLESPACE pg_default")

}
private void dropExistingSetup(){
	jdbcHelper.executeUpdate("DROP TABLE IF  EXISTS public.module_permissions");
	jdbcHelper.executeUpdate("DROP INDEX IF  EXISTS public.module_permissions_module_name_idx");
	jdbcHelper.executeUpdate("DROP SEQUENCE IF  EXISTS public.module_permissions_permission_id_seq");
}
