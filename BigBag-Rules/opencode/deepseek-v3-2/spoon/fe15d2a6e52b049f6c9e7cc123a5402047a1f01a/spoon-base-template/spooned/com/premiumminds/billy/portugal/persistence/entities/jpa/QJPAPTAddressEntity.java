package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTAddressEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAAddressEntity;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTAddressEntity is a Querydsl query type for JPAPTAddressEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTAddressEntity extends EntityPathBase<JPAPTAddressEntity> {
    private static final long serialVersionUID = 2136373420L;

    public static final QJPAPTAddressEntity jPAPTAddressEntity = new QJPAPTAddressEntity("jPAPTAddressEntity");

    public final QJPAAddressEntity _super = new QJPAAddressEntity(this);

    // inherited
    public final BooleanPath active = _super.active;

    // inherited
    public final StringPath building = _super.building;

    // inherited
    public final StringPath city = _super.city;

    // inherited
    public final StringPath country = _super.country;

    // inherited
    public final DateTimePath<Date> createTimestamp = _super.createTimestamp;

    // inherited
    public final StringPath details = _super.details;

    // inherited
    public final NumberPath<Integer> entityVersion = _super.entityVersion;

    // inherited
    public final NumberPath<Long> id = _super.id;

    // inherited
    public final StringPath number = _super.number;

    // inherited
    public final StringPath postalCode = _super.postalCode;

    // inherited
    public final StringPath region = _super.region;

    // inherited
    public final StringPath streetName = _super.streetName;

    // inherited
    public final StringPath uid = _super.uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp = _super.updateTimestamp;

    public QJPAPTAddressEntity(String variable) {
        super(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTAddressEntity.class, forVariable(variable));
    }

    public QJPAPTAddressEntity(Path<? extends JPAPTAddressEntity> path) {
        super(path.getType(), path.getMetadata());
    }

    public QJPAPTAddressEntity(PathMetadata metadata) {
        super(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTAddressEntity.class, metadata);
    }
}
