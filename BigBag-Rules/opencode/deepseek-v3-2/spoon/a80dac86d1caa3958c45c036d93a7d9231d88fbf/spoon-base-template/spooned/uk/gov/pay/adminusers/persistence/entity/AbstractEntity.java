package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.MappedSuperclass
public abstract class AbstractEntity implements java.io.Serializable {
    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.IDENTITY)
    private java.lang.Integer id;

    @javax.persistence.Version
    @javax.persistence.Column(name = "version")
    private java.lang.Long version;

    /**
     * For JPA
     */
    public AbstractEntity() {
    }

    public java.lang.Long getVersion() {
        return version;
    }

    public java.lang.Integer getId() {
        return id;
    }

    public void setId(java.lang.Integer id) {
        this.id = id;
    }
}
