package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTShippingPointEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAAddressEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAShippingPointEntity;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.types.dsl.PathInits;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTShippingPointEntity is a Querydsl query type for JPAPTShippingPointEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTShippingPointEntity extends EntityPathBase<JPAPTShippingPointEntity> {
    private static final long serialVersionUID = 680084218L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTShippingPointEntity jPAPTShippingPointEntity = new QJPAPTShippingPointEntity("jPAPTShippingPointEntity");

    public final QJPAShippingPointEntity _super;

    // inherited
    public final BooleanPath active;

    // inherited
    public final QJPAAddressEntity address;

    // inherited
    public final DateTimePath<Date> createTimestamp;

    // inherited
    public final DateTimePath<Date> date;

    // inherited
    public final StringPath deliveryId;

    // inherited
    public final NumberPath<Integer> entityVersion;

    // inherited
    public final NumberPath<Long> id;

    // inherited
    public final StringPath locationId;

    // inherited
    public final StringPath ucr;

    // inherited
    public final StringPath uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    // inherited
    public final StringPath warehouseId;

    public QJPAPTShippingPointEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTShippingPointEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTShippingPointEntity(Path<? extends JPAPTShippingPointEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTShippingPointEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTShippingPointEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTShippingPointEntity.class, metadata, inits);
    }

    public QJPAPTShippingPointEntity(Class<? extends JPAPTShippingPointEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPAShippingPointEntity(type, metadata, inits);
        this.active = _super.active;
        this.address = _super.address;
        this.createTimestamp = _super.createTimestamp;
        this.date = _super.date;
        this.deliveryId = _super.deliveryId;
        this.entityVersion = _super.entityVersion;
        this.id = _super.id;
        this.locationId = _super.locationId;
        this.ucr = _super.ucr;
        this.uid = _super.uid;
        this.updateTimestamp = _super.updateTimestamp;
        this.warehouseId = _super.warehouseId;
    }
}
