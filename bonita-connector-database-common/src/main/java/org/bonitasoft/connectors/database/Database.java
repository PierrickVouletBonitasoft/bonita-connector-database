/**
 * Copyright (C) 2009-2011 BonitaSoft S.A.
 * BonitaSoft, 31 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.bonitasoft.connectors.database;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.bonitasoft.engine.connector.ConnectorException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * @author Matthieu Chaffotte
 * @author Baptiste Mesta
 * @author Frédéric Bouquet
 */
public class Database {

	private final Connection connection;

	private Context ctx;

	public Database(final String dataSource, final Properties properties) throws NamingException, SQLException {
		ctx = new InitialContext(properties);
		final DataSource ds = (DataSource) ctx.lookup(dataSource);
		connection = ds.getConnection();
	}

	public Database(final String driver, final String url, final String username, final String password) throws ClassNotFoundException, InstantiationException,
	IllegalAccessException, SQLException, NoSuchAlgorithmException, UnsupportedEncodingException, NamingException {
		Class.forName(driver);
		connection = getHikariDataSource(driver, url, username, password).getConnection();
	}

	private HikariDataSource getHikariDataSource(String driver, String url, String username, String password) throws NamingException, NoSuchAlgorithmException, UnsupportedEncodingException {
		ctx = getHikariContext();
		Object object = ctx.lookup(getHashHikariInfo(driver, url, username, password));
		if(object != null && object instanceof HikariDataSource) {
			synchronized (Integer.class) {
				object = ctx.lookup(getHashHikariInfo(driver, url, username, password));
				if(object != null && object instanceof HikariDataSource) {
					HikariConfig config = new HikariConfig();
					config.setJdbcUrl(url);
					config.setUsername(username);
					config.setPassword(password);
					config.addDataSourceProperty("cachePrepStmts", "true");
					config.addDataSourceProperty("prepStmtCacheSize", "250");
					config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
					ctx.bind(getHashHikariInfo(driver, url, username, password), new HikariDataSource(config));
					object = ctx.lookup(getHashHikariInfo(driver, url, username, password));
				}
			}

		}

		return (HikariDataSource) object;
	}
	
	static private Context getHikariContext() throws NamingException {
		Properties properties = new Properties();
//		properties.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
//		properties.put(Context.PROVIDER_URL, "ldap://localhost:389/dc=etcee,dc=com");
//		properties.put(Context.SECURITY_PRINCIPAL, "name");
//		properties.put(Context.SECURITY_CREDENTIALS, "password");
//		properties.put(Context.PROVIDER_URL, "hikari:");
		return new InitialContext();
	}

	private String getHashHikariInfo(String driver, String url, String username, String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(("hiraki:/" + driver + ":" + url + ":" + username + ":" + password).getBytes("UTF-8"));
		return new String(md.digest());
	}

	static public List<HikariDataSource> getCurrentHirakiDataSources() throws NamingException {
		Context ctx = getHikariContext();
		List<HikariDataSource> list = new ArrayList<HikariDataSource>();
		NamingEnumeration<Binding> bindings = ctx.listBindings("");
		while(bindings.hasMore()) {
			Binding binding = (Binding)bindings.next();
			if(binding.getObject() instanceof HikariDataSource) {
				list.add((HikariDataSource) binding.getObject());
			}
		}
		bindings.close();

		return list;
	}

	public void disconnect() throws SQLException, NamingException {
		if (connection != null && !connection.isClosed()) {
			connection.close();
		}
		if (ctx != null) {
			ctx.close();
		}
	}

	public ResultSet select(final String query) throws SQLException {
		final Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		return statement.executeQuery(query);
	}

	public boolean executeCommand(final String command) throws SQLException, ConnectorException {
		Statement statement=null;
		boolean isExecuted=false;
		try {
			statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			isExecuted = statement.execute(command);
		} catch (SQLException e) {
			throw new ConnectorException(e);
		} finally {
			if (statement!=null){
				statement.close();
			}
		}
		return isExecuted;
	}

	public void executeBatch(final List<String> commands, final boolean commit) throws SQLException, ConnectorException {
		Statement statement = null;
		try {
			connection.setAutoCommit(false);
			statement = connection.createStatement();
			for (final String command : commands) {
				statement.addBatch(command);
			}
			statement.executeBatch();

			if (commit) {
				connection.commit();
			}
		} catch (SQLException e){
			throw new ConnectorException(e);
		} finally {
			if (statement !=null){
				statement.close();
			}
		}
	}

}
