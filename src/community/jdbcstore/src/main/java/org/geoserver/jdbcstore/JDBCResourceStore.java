/* Copyright (c) 2001 - 2014 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.jdbcstore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.commons.lang.NotImplementedException;
import org.geoserver.jdbcconfig.internal.Util;
import org.geoserver.platform.resource.Paths;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.ResourceStore;
import org.geoserver.platform.resource.Resource.Type;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.google.common.base.Preconditions;

/**
 * Implementation of ResourceStore backed by a JDBC DataSource.
 * 
 * @author Kevin Smith, Boundless
 *
 */
public class JDBCResourceStore implements ResourceStore {
    
    private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(JDBCResourceStore.class);
    
    private DataSource ds;
    private JDBCResourceStoreProperties config;
    private NamedParameterJdbcOperations template;
    
    public JDBCResourceStore(DataSource ds, JDBCResourceStoreProperties config) {
        this.ds = ds;
        this.config = config;
        template = new NamedParameterJdbcTemplate(ds);
        
        Connection c;
        try {
            c = ds.getConnection();
        } catch (SQLException ex) {
            throw new IllegalArgumentException("Could not connect to provided DataSource.",ex);
        }
        try {
            if(config.isInitDb()) {
                LOGGER.log(Level.INFO, "Initializing Resource Store Database.");
                initEmptyDB(ds);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not initialize resource database.",ex);
        }
        finally {
            try {
                c.close();
            } catch (SQLException ex) {
                throw new IllegalArgumentException("Error while closing connection.",ex);
            }
        }
    }

    
    @Override
    public Resource get(String path) {
        
        List<String> namesList = Paths.names(path);
        String[] names = namesList.toArray(new String[namesList.size()]);
        Selector sel = new PathSelector(0, names);
        
        String name = names[names.length-1];
        
        Object[] results = query(sel, Field.OID, Field.DIRECTORY, Field.PARENT);
        if(results==null) {
            int parent = 0;
            if(names.length>1){
                names = Arrays.copyOf(names, names.length-1);
                sel = new PathSelector(0, names);
                results = query(sel, Field.OID, Field.DIRECTORY, Field.PARENT);
                parent= (Integer)results[0];
            }
            return new JDBCResource(-1, Type.UNDEFINED, name, parent);
        } else {
            return new JDBCResource((Integer)results[0], ((Boolean)results[1])?Type.DIRECTORY:Type.RESOURCE, name, (Integer)results[2]);
        }
    }
    Resource get(int oid) {
        Object[] results = query(new OIDSelector(oid), Field.OID, Field.DIRECTORY, Field.NAME, Field.PARENT);
        if(results==null) {
            return null;
        } else {
            return new JDBCResource((Integer)results[0], ((Boolean)results[1])?Type.DIRECTORY:Type.RESOURCE, (String)results[2], (Integer)results[3]);
        }
    }
    
    @Override
    public boolean remove(String path) {
        throw new NotImplementedException();
    }
    
    @Override
    public boolean move(String path, String target) {
        throw new NotImplementedException();
    }
    @Override
    public String toString() {
        return "ResourceStore "+ds;
    }
    
    /**
     * Direct implementation of Resource.
     * <p>
     * This implementation is a stateless data object, acting as a simple handle around a File.
     */
    class JDBCResource implements Resource {
        int oid;
        int parent;
        Type type;
        String name;
        
        public JDBCResource(int oid, Type type, String name, int parent) {
            super();
            assert((oid<0)==(type==Type.UNDEFINED)); // Must have an oid xor be undefined
            assert((parent<0)==(oid==0)); // Must have a parent xor be the root element
            this.oid = oid;
            this.type = type;
            this.name = name;
            this.parent = parent;
        }

        @Override
        public String path() {
            List<String> names;
            if(type==Type.UNDEFINED) {
                names = findPath(this.parent);
                names.add(name);
            } else {
                names = findPath(this.oid);
            }
            return Paths.path(names.toArray(new String[names.size()]));
        }

        @Override
        public String name() {
            if(type!=Type.UNDEFINED)
                name = (String) query(new OIDSelector(oid), Field.NAME)[0];
            
            return name;
        }

        @Override
        public InputStream in() {
            // TODO Auto-generated method stub
            throw new NotImplementedException();
        }

        @Override
        public OutputStream out() {
            // TODO Auto-generated method stub
            throw new NotImplementedException();
        }

        @Override
        public File file() {
            // TODO Auto-generated method stub
            throw new NotImplementedException();
        }

        @Override
        public File dir() {
            // TODO Auto-generated method stub
            throw new NotImplementedException();
        }

        @Override
        public long lastmodified() {
            Timestamp t = (Timestamp) query(new OIDSelector(oid), Field.LAST_MODIFIED)[0];
            return t.getTime();
        }

        @Override
        public Resource parent() {
            int parent = (Integer) query(new OIDSelector(oid), Field.PARENT)[0];
            return JDBCResourceStore.this.get(parent);
        }

        @Override
        public Resource get(String resourcePath) {
            // TODO Auto-generated method stub
            throw new NotImplementedException();
        }

        @Override
        public List<Resource> list() {
            // TODO Auto-generated method stub
            throw new NotImplementedException();
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + oid;
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            JDBCResource other = (JDBCResource) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (oid != other.oid)
                return false;
            if (type != other.type)
                return false;
            return true;
        }

        private JDBCResourceStore getOuterType() {
            return JDBCResourceStore.this;
        }
        
        
    }
    
    enum Field {
        OID("oid","oid") {
            @Override
            Object getValue(ResultSet rs) throws SQLException {
                return rs.getInt(fieldName);
            }
        },
        NAME("name", "name") {
            @Override
            Object getValue(ResultSet rs) throws SQLException {
                return rs.getString(fieldName);
            }
        },
        PARENT("parent","parent") {
            @Override
            Object getValue(ResultSet rs) throws SQLException {
                int i = rs.getInt(fieldName);
                if(rs.wasNull()) return null;
                return i;
            }
        },
        LAST_MODIFIED("last_modified","last_modified") {
            @Override
            Object getValue(ResultSet rs) throws SQLException {
                return rs.getTimestamp(fieldName);
            }
        },
        DIRECTORY("directory","content IS NULL AS directory") {
            @Override
            Object getValue(ResultSet rs) throws SQLException {
                return rs.getBoolean(fieldName);
            }
        },
        CONTENT("content","content") {
            @Override
            Object getValue(ResultSet rs) throws SQLException {
                return rs.getBlob(fieldName);
            }
        },
       ;
        final String fieldName;
        final String fieldExpression;
        Field(String name, String expression) {
            this.fieldName = name;
            this.fieldExpression = expression;
        }
        abstract Object getValue(ResultSet rs) throws SQLException;
    }
    
    static interface Selector {
        StringBuilder appendCondition(StringBuilder sb);
    }
    
    static class OIDSelector implements Selector {
        int oid;
        OIDSelector(int i) {
            oid=i;
        }
        @Override
        public StringBuilder appendCondition(StringBuilder sb) {
            sb.append("oid = ");
            sb.append(oid);
            return sb;
        }
        
    }
    
    static class PathSelector implements Selector {
        String[] path;
        int oid;
        PathSelector(int oid, String... path) {
            this.path = path;
        }
        static Selector parse(int oid, String path) {
            return new PathSelector(oid, Paths.names(path).toArray(new String[]{}));
        }
        
        // FIXME do this more safely rather than inserting the strings directly
        StringBuilder oidQuery(StringBuilder builder, int i) {
            assert(i>=0);
            assert(i<path.length);
            
            if(i>0) {
                builder.append("SELECT oid FROM resource WHERE parent=(");
                oidQuery(builder, i-1);
                builder.append(") and name='");
                builder.append(path[i]);
                builder.append("'");
            } else {
                builder.append("SELECT oid FROM resource WHERE parent=");
                builder.append(oid);
                builder.append(" and name='");
                builder.append(path[i]);
                builder.append("'");
            }
            
            return builder;
        }
        
        @Override
        public StringBuilder appendCondition(StringBuilder sb) {
            sb.append("oid = (");
            oidQuery(sb, path.length-1);
            sb.append(")");
            return sb;
        }
        
    }
    
    List<String> findPath(int oid) {
        Connection c;
        try {
            c = ds.getConnection();
        } catch (SQLException ex) {
            throw new IllegalArgumentException("Could not connect to DataSource.",ex);
        } 
        try {
            String sql;
            if (true) {
                sql = "CALL path_to(?);";
            } else {
                sql = "WITH RECURSIVE path(oid, name, parent, depth) AS (\n    SELECT oid, name, parent, 0 FROM resource WHERE oid=?\n  UNION ALL\n    SELECT cur.oid, cur.name, cur.parent, rec.depth+1\n      FROM resource AS cur, path AS rec\n      WHERE cur.oid=rec.parent\n  )\nSELECT oid, name FROM path;";
            }
            PreparedStatement stmt = c.prepareStatement(sql);
            stmt.setInt(1, oid);
            LOGGER.log(Level.INFO, "Looking up path: {0}", stmt);
            ResultSet rs = stmt.executeQuery();
            
            List<String> result = new LinkedList<String>();
            
            while(rs.next()) {
                int foundOid = rs.getInt("oid");
                String foundName = rs.getString("name");
                
                if(foundOid==0) {
                    assert(rs.isFirst());
                } else {
                    result.add(foundName);
                }
            }
            return result;
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
        finally {
            try {
                c.close();
            } catch (SQLException ex) {
                throw new IllegalArgumentException("Error while closing connection.",ex);
            }
        }

    }
    
    Object[] query(Selector sel, Field... fields) {
        StringBuilder builder = new StringBuilder();
        
        builder.append("SELECT ");
        {
            int i=0;
            for(Field field:fields) {
                if(i++>0) builder.append(", ");
                builder.append(field.fieldExpression);
            }
        }
        builder.append(" FROM resource WHERE ");
        sel.appendCondition(builder);
        builder.append(";");
        
        Connection c;
        
        try {
            c = ds.getConnection();
        } catch (SQLException ex) {
            throw new IllegalArgumentException("Could not connect to DataSource.",ex);
        } 
        try {
            Statement stmt = c.createStatement();
            LOGGER.log(Level.INFO, builder.toString());
            ResultSet rs = stmt.executeQuery(builder.toString());
            
            if(rs.next()) {
                // Found something
                
                // Should only have found one entry
                assert(rs.isLast()); 
                
                Object[] result = new Object[fields.length];
                
                for(int i = 0; i< fields.length; i++) {
                    result[i] = fields[i].getValue(rs);
                }
                return result;
            } else {
                // Nothing there yet
                return null;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
        finally {
            try {
                c.close();
            } catch (SQLException ex) {
                throw new IllegalArgumentException("Error while closing connection.",ex);
            }
        }
    }
    
    private void initEmptyDB(DataSource ds) throws IOException {
        
        Util.runScript(config.getInitScript(), template.getJdbcOperations(), null);
    }
    
    String[] path_to(Connection conn, int oid) throws SQLException {
        LinkedList<String> result = new LinkedList<String>();
        PreparedStatement stmt = conn.prepareStatement("SELECT parent, oid, name FROM resource WHERE oid = ?;");
        try {
            Integer foundParent=oid; // Pretend there was some child of the target node
            String foundName;
            while(true){
                stmt.setInt(0, foundParent);
                ResultSet rs = stmt.executeQuery();
                if(!rs.next()) {
                    throw new SQLException("Could not find resource "+foundParent+" while generating path of resource "+oid);
                }
                
                foundParent = rs.getInt("parent");
                if(rs.wasNull()) foundParent=null;
                foundName = rs.getString("name");
                
                result.addFirst(foundName);
                
                if(foundParent==null) break;
            }
            return result.toArray(new String[result.size()]);
        } finally {
            stmt.close();
        }
    }
}
