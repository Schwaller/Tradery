package com.tradery.ai.pipeline;

import com.tradery.ai.pipeline.schema.EntityDescriptor;
import com.tradery.ai.pipeline.schema.EntityTypeDescriptor;
import com.tradery.ai.pipeline.schema.ExistingEntity;
import com.tradery.ai.pipeline.schema.RelationshipTypeDescriptor;

import java.util.List;

/**
 * Input to the discovery pipeline. Use {@link #builder()} to construct.
 */
public record DiscoveryRequest(
    EntityDescriptor entity,
    List<RelationshipTypeDescriptor> relationshipTypes,
    List<EntityTypeDescriptor> targetEntityTypes,
    List<ExistingEntity> existingEntities,
    String systemContext
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EntityDescriptor entity;
        private List<RelationshipTypeDescriptor> relationshipTypes;
        private List<EntityTypeDescriptor> targetEntityTypes;
        private List<ExistingEntity> existingEntities;
        private String systemContext;

        public Builder entity(EntityDescriptor entity) {
            this.entity = entity;
            return this;
        }

        public Builder relationshipTypes(List<RelationshipTypeDescriptor> types) {
            this.relationshipTypes = types;
            return this;
        }

        public Builder targetEntityTypes(List<EntityTypeDescriptor> types) {
            this.targetEntityTypes = types;
            return this;
        }

        public Builder existingEntities(List<ExistingEntity> entities) {
            this.existingEntities = entities;
            return this;
        }

        public Builder systemContext(String context) {
            this.systemContext = context;
            return this;
        }

        public DiscoveryRequest build() {
            if (entity == null) throw new IllegalStateException("entity is required");
            return new DiscoveryRequest(entity, relationshipTypes, targetEntityTypes,
                existingEntities, systemContext);
        }
    }
}
