package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTApplicationEntity;
import com.premiumminds.billy.persistence.entities.jpa.JPAContactEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAApplicationEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAContactEntity;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.types.dsl.PathInits;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTApplicationEntity is a Querydsl query type for JPAPTApplicationEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTApplicationEntity extends EntityPathBase<JPAPTApplicationEntity> {
    private static final long serialVersionUID = 1916963464L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTApplicationEntity jPAPTApplicationEntity = new QJPAPTApplicationEntity("jPAPTApplicationEntity");

    public final QJPAApplicationEntity _super;

    // inherited
    public final BooleanPath active;

    // inherited
    public final ListPath<JPAContactEntity, QJPAContactEntity> contacts;

    // inherited
    public final DateTimePath<Date> createTimestamp;

    // inherited
    public final StringPath developerName;

    // inherited
    public final StringPath developerTaxId;

    // inherited
    public final StringPath developerTaxIdISOCountryCode;

    // inherited
    public final NumberPath<Integer> entityVersion;

    // inherited
    public final NumberPath<Long> id;

    // inherited
    public final QJPAContactEntity mainContact;

    // inherited
    public final StringPath name;

    public final NumberPath<Integer> number = createNumber("number", Integer.class);

    public final StringPath path = createString("path");

    // inherited
    public final StringPath uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    // inherited
    public final StringPath version;

    // inherited
    public final StringPath website;

    public QJPAPTApplicationEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTApplicationEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTApplicationEntity(Path<? extends JPAPTApplicationEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTApplicationEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTApplicationEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTApplicationEntity.class, metadata, inits);
    }

    public QJPAPTApplicationEntity(Class<? extends JPAPTApplicationEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPAApplicationEntity(type, metadata, inits);
        this.active = _super.active;
        this.contacts = _super.contacts;
        this.createTimestamp = _super.createTimestamp;
        this.developerName = _super.developerName;
        this.developerTaxId = _super.developerTaxId;
        this.developerTaxIdISOCountryCode = _super.developerTaxIdISOCountryCode;
        this.entityVersion = _super.entityVersion;
        this.id = _super.id;
        this.mainContact = _super.mainContact;
        this.name = _super.name;
        this.uid = _super.uid;
        this.updateTimestamp = _super.updateTimestamp;
        this.version = _super.version;
        this.website = _super.website;
    }
}
