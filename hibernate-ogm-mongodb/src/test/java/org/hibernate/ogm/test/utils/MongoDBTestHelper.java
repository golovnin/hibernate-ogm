/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.hibernate.ogm.test.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mongodb.MongoException;
import org.bson.types.ObjectId;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.ogm.datastore.mongodb.Environment;
import org.hibernate.ogm.datastore.mongodb.impl.MongoDBDatastoreProvider;
import org.hibernate.ogm.datastore.spi.DatastoreProvider;
import org.hibernate.ogm.dialect.mongodb.MongoDBDialect;
import org.hibernate.ogm.grid.EntityKey;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.hibernate.ogm.logging.mongodb.impl.Log;
import org.hibernate.ogm.logging.mongodb.impl.LoggerFactory;

/**
 * @author Guillaume Scheibel <guillaume.scheibel@gmail.com>
 * @author Sanne Grinovero <sanne@hibernate.org>
 */
public class MongoDBTestHelper implements TestableGridDialect {

	private static final Log log = LoggerFactory.getLogger();

	static {
		// Read host and port from environment variable
		// Maven's surefire plugin set it to the string 'null'
		String mongoHostName = System.getenv( "MONGODB_HOSTNAME" );
		if ( isNotNull( mongoHostName ) ) {
			System.getProperties().setProperty( Environment.MONGODB_HOST, mongoHostName );
		}
		String mongoPort = System.getenv( "MONGODB_PORT" );
		if ( isNotNull( mongoPort ) ) {
			System.getProperties().setProperty( Environment.MONGODB_PORT, mongoPort );
		}
	}

	private static boolean isNotNull(String mongoHostName) {
		return mongoHostName != null && mongoHostName.length() > 0 && ! "null".equals( mongoHostName );
	}

	@Override
	public int entityCacheSize(SessionFactory sessionFactory) {
		MongoDBDatastoreProvider provider = MongoDBTestHelper.getProvider( sessionFactory );
		DB db = provider.getDatabase();
		int count = 0;
		for ( String collectionName : db.getCollectionNames() ) {
			if ( !collectionName.startsWith( "system." ) && !collectionName.startsWith( MongoDBDialect.ASSOCIATIONS_COLLECTION_PREFIX ) ) {
				//DBObject query = new BasicDBObject( "table" , new BasicDBObject( "$exists", false) );
				//count += db.getCollection( collectionName ).find( query ).count();
				count += db.getCollection( collectionName ).count();
			}
		}
		return count;
	}

	private int countAssociationOnCollection(DBCollection collection) {
		DBCursor cursor = collection.find( new BasicDBObject(), new BasicDBObject( MongoDBDialect.ID_FIELDNAME, 0 ) );
		Iterator<DBObject> it = cursor.iterator();
		int count = 0;
		while ( it.hasNext() ) {
			DBObject current = it.next();
			Map<?, ?> map = current.toMap();
			count += this.countAssociationOnDocument( map );
		}
		return count;
	}

	private int countAssociationOnDocument(Map<?, ?> map) {
		int count = 0;
		for ( Object key : map.keySet() ) {
			Object value = map.get( key );
			if ( value instanceof Map ) {
				count += this.countAssociationOnDocument( (Map<?, ?>) value );
			}
			else {
				count += map.get( key ).getClass().equals( ObjectId.class ) ? 1 : 0;
			}
		}
		return count;
	}

	@Override
	public int associationCacheSize(SessionFactory sessionFactory) {
		MongoDBDatastoreProvider provider = MongoDBTestHelper.getProvider( sessionFactory );
		DB db = provider.getDatabase();
		int generalCount = 0;
		for ( String collectionName : db.getCollectionNames() ) {
			//DBObject query = new BasicDBObject("table", new BasicDBObject( "$exists", true) );
			//generalCount += db.getCollection( collectionName ).find( query ).count();
			if ( collectionName.startsWith( MongoDBDialect.ASSOCIATIONS_COLLECTION_PREFIX ) )
				generalCount += db.getCollection( collectionName ).count();
		}
		return generalCount;
	}
	
	@Override
	public Map<String, Object> extractEntityTuple(SessionFactory sessionFactory, EntityKey key) {
		MongoDBDatastoreProvider provider = MongoDBTestHelper.getProvider( sessionFactory );
		DBObject finder = new BasicDBObject( MongoDBDialect.ID_FIELDNAME, key.getColumnValues()[0] );
		DBObject result = provider.getDatabase().getCollection( key.getTable() ).findOne( finder );
		return result.toMap();
	}

	@Override
	public boolean backendSupportsTransactions() {
		return false;
	}

	private static MongoDBDatastoreProvider getProvider(SessionFactory sessionFactory) {
		DatastoreProvider provider = ( (SessionFactoryImplementor) sessionFactory ).getServiceRegistry().getService(
				DatastoreProvider.class );
		if ( !( MongoDBDatastoreProvider.class.isInstance( provider ) ) ) {
			throw new RuntimeException( "Not testing with MongoDB, cannot extract underlying cache" );
		}
		return MongoDBDatastoreProvider.class.cast( provider );
	}

	@Override
	public void dropSchemaAndDatabase(SessionFactory sessionFactory) {
		MongoDBDatastoreProvider provider = getProvider( sessionFactory );
		try {
			provider.getDatabase().dropDatabase();
		}
		catch ( MongoException ex ) {
			throw log.unableToDropDatabase( ex, provider.getDatabase().getName() );
		}
	}

	@Override
	public Map<String, String> getEnvironmentProperties() {
		//read variables from the System properties set in the static initializer
		Map<String,String> envProps = new HashMap<String, String>(2);
		String host = System.getProperties().getProperty( Environment.MONGODB_HOST );
		if ( host != null && host.length() > 0 ) {
			envProps.put( Environment.MONGODB_HOST, host );
		}
		String port = System.getProperties().getProperty( Environment.MONGODB_PORT );
		if ( port != null && port.length() > 0 ) {
			envProps.put( Environment.MONGODB_PORT, port );
		}
		return envProps;
	}
}
