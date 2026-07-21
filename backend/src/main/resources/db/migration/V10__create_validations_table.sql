CREATE TABLE validations (
                             id UUID PRIMARY KEY,

                             proposal_id UUID NOT NULL,

                             decision VARCHAR(20) NOT NULL,

                             validated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                             validated_by UUID NOT NULL,

                             comment TEXT,

                             CONSTRAINT uk_validation_proposal_id
                                 UNIQUE (proposal_id),

                             CONSTRAINT fk_validation_proposal
                                 FOREIGN KEY (proposal_id)
                                     REFERENCES validatable_proposals(id)
);

CREATE INDEX idx_validation_proposal_id
    ON validations(proposal_id);

CREATE INDEX idx_validation_validated_by
    ON validations(validated_by);