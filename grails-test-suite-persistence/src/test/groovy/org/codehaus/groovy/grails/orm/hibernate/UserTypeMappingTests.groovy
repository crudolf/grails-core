package org.codehaus.groovy.grails.orm.hibernate

import javax.sql.DataSource

/**
* @author Graeme Rocher
*/
class UserTypeMappingTests extends AbstractGrailsHibernateTests{

    protected void onSetUp() {
        gcl.parseClass '''
import org.hibernate.type.*

class UserTypeMappingTest {
    Long id
    Long version

    Boolean active

    static mapping = {
        table 'type_test'
        columns {
            active (column: 'active', type: YesNoType)
        }
    }
}
'''
        gcl.parseClass '''
import org.hibernate.usertype.UserType

import org.hibernate.type.Type
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import org.hibernate.Hibernate
import org.hibernate.HibernateException
import java.sql.Types

class WeightUserType implements UserType {

    private static final int[] SQL_TYPES = [ Types.INTEGER ]
    int[] sqlTypes() {
        return SQL_TYPES
    }

    Class returnedClass() { Weight }

    boolean equals(Object x, Object y) throws HibernateException {
        if (x == y) {
            return true
        }
        if (x == null || y == null) {
            return false
        }
        return x.equals(y)
    }

    int hashCode(Object x) throws HibernateException {
        return x.hashCode()
    }

    Object nullSafeGet(ResultSet resultSet,  String[] names, Object owner) throws HibernateException, SQLException {
        Weight result = null
        int pounds = resultSet.getInt(names[0])
        if (!resultSet.wasNull()) {
           result = new Weight(pounds)
        }
        return result
     }

    void nullSafeSet(PreparedStatement statement,  Object value, int index)   throws HibernateException, SQLException {
        if (value == null) {
            statement.setNull(index)
        }
        else {
            Integer pounds = value.pounds
            statement.setInt(index, pounds)
        }
    }

    Object deepCopy(Object value) { value }

    boolean isMutable() { false }

    Serializable disassemble(Object value) throws HibernateException {
        value
    }

    Object assemble(Serializable state, Object owner) { state }

    Object replace(Object original, Object target, Object owner) { original }
}

class Weight {
    Integer pounds
    Weight(Integer pounds) {
        this.pounds = pounds
    }
}
'''

        gcl.parseClass '''
class UserTypeMappingTestsPerson {
    Long id
    Long version
    String name
    Weight weight

    static constraints = {
        name(unique: true)
        weight(nullable: true)
    }

    static mapping = {
        columns {
            weight(type:WeightUserType)
        }
    }
}
'''
    }

    void testCustomUserType() {
        def personClass = ga.getDomainClass("UserTypeMappingTestsPerson").clazz
        def weightClass = ga.classLoader.loadClass("Weight")

        def person = personClass.newInstance(name:"Fred", weight:weightClass.newInstance(200))

        person.save(flush:true)
        session.clear()

        person = personClass.get(1)

        assertNotNull person
        assertEquals 200, person.weight.pounds
    }

    void testUserTypeMapping() {

        def clz = ga.getDomainClass("UserTypeMappingTest").clazz

        assertNotNull clz.newInstance(active:true).save(flush:true)

        DataSource ds = (DataSource)applicationContext.getBean('dataSource')

        def con
        try {
            con = ds.getConnection()
            def statement = con.prepareStatement("select * from type_test")
            def result = statement.executeQuery()
            assertTrue result.next()
            def value = result.getString('active')

            assertEquals "Y", value
        }
        finally {
            con.close()
        }
    }

    void testUserTypePropertyMetadata() {
        def personDomainClass = ga.getDomainClass("UserTypeMappingTestsPerson")
        def personClass = personDomainClass.clazz
        def weightClass = ga.classLoader.loadClass("Weight")

        def person = personClass.newInstance(name:"Fred", weight:weightClass.newInstance(200))

        // the metaClass should report the correct type, not Object
        assertEquals weightClass, personClass.metaClass.hasProperty(person, "weight").type

        // GrailsDomainClassProperty should not appear to be an association
        def prop = personDomainClass.getPropertyByName("weight")
        assertFalse prop.isAssociation()
        assertFalse prop.isOneToOne()
        assertEquals weightClass, prop.type
    }
}
