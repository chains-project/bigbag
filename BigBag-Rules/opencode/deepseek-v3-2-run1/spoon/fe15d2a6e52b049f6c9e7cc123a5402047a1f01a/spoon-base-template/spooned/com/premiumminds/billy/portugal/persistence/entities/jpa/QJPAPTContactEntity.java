package com.premiumminds.billy.portugal.persistence.entities.jpa;
import EntityPathBase;
import JPAPTContactEntity;
import com.premiumminds.billy.persistence.entities.jpa.QJPAContactEntity;
import java.util.Date;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.*;
import static com.querydsl.core.types.PathMetadataFactory.*;
/**
 * QJPAPTContactEntity is a Querydsl query type for JPAPTContactEntity
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QJPAPTContactEntity extends EntityPathBase<JPAPTContactEntity> {
    private static final long serialVersionUID = -800968744L;

    public static final QJPAPTContactEntity jPAPTContactEntity = new QJPAPTContactEntity("jPAPTContactEntity");

    public final QJPAContactEntity _super = new QJPAContactEntity(this);

    // inherited
    public final BooleanPath active = _super.active;

    // inherited
    public final DateTimePath<Date> createTimestamp = _super.createTimestamp;

    // inherited
    public final StringPath email = _super.email;

    // inherited
    public final NumberPath<Integer> entityVersion = _super.entityVersion;

    // inherited
    public final StringPath fax = _super.fax;

    // inherited
    public final NumberPath<Long> id = _super.id;

    // inherited
    public final StringPath mobile = _super.mobile;

    // inherited
    public final StringPath name = _super.name;

    // inherited
    public final StringPath phone = _super.phone;

    // inherited
    public final StringPath uid = _super.uid;

    // inherited
    public final DateTimePath<Date> updateTimestamp = _super.updateTimestamp;

    // inherited
    public final StringPath website = _super.website;

    public QJPAPTContactEntity(String variable) {
        super(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTContactEntity.class, forVariable(variable));
    }

    public QJPAPTContactEntity(Path<? extends JPAPTContactEntity> path) {
        super(path.getType(), path.getMetadata());
    }

    public QJPAPTContactEntity(PathMetadata metadata) {
        super(com.premiumminds.billy.portugal.persistence.entities.jpa.JPAPTContactEntity.class, metadata);
    }
}
