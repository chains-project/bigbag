package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTRegionContextEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAContextEntity;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.types.dsl.PathInits;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTRegionContextEntity is a Querydsl query type for JPAPTRegionContextEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTRegionContextEntity extends EntityPathBase<JPAPTRegionContextEntity> {
    private static final long serialVersionUID = 1943221203L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QJPAPTRegionContextEntity jPAPTRegionContextEntity = new QJPAPTRegionContextEntity("jPAPTRegionContextEntity");

    public final QJPAContextEntity _super;

    // inherited
    public final BooleanPath active;

    // inherited
    public final DateTimePath<Date> createTimestamp;

    // inherited
    public final StringPath description;

    // inherited
    public final NumberPath<Integer> entityVersion;

    // inherited
    public final NumberPath<Long> id;

    // inherited
    public final StringPath name;

    // inherited
    public final QJPAContextEntity parent;

    public final StringPath regionCode = createString("regionCode");

    // inherited
    public final StringPath uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp;

    public QJPAPTRegionContextEntity(String variable) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTRegionContextEntity.class, forVariable(variable), INITS);
    }

    public QJPAPTRegionContextEntity(Path<? extends JPAPTRegionContextEntity> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QJPAPTRegionContextEntity(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QJPAPTRegionContextEntity(PathMetadata metadata, PathInits inits) {
        this(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTRegionContextEntity.class, metadata, inits);
    }

    public QJPAPTRegionContextEntity(Class<? extends JPAPTRegionContextEntity> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this._super = new QJPAContextEntity(type, metadata, inits);
        this.active = _super.active;
        this.createTimestamp = _super.createTimestamp;
        this.description = _super.description;
        this.entityVersion = _super.entityVersion;
        this.id = _super.id;
        this.name = _super.name;
        this.parent = _super.parent;
        this.uid = _super.uid;
        this.updateTimestamp = _super.updateTimestamp;
    }
}
