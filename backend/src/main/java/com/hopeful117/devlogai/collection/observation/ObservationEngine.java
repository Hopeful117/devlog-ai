package com.hopeful117.devlogai.collection.observation;

import com.hopeful117.devlogai.fact.entity.Fact;

import java.util.List;

public interface ObservationEngine {

    List<DerivedObservation> derive(List<Fact> facts);
}
