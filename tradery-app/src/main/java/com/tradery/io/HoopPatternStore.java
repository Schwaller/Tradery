package com.tradery.io;

import com.tradery.model.HoopPattern;

import java.io.File;

/**
 * Reads and writes HoopPattern YAML files.
 * Each pattern has its own folder: ~/.tradery/hoops/{id}/hoop.yaml
 *
 * Backward compatible: auto-migrates legacy hoop.json to hoop.yaml
 *
 * Claude Code can directly read/write these files.
 */
public class HoopPatternStore extends YamlStore<HoopPattern> {

    public HoopPatternStore(File directory) {
        super(directory);
    }

    @Override
    protected String getFileName() {
        return "hoop.yaml";
    }

    @Override
    protected Class<HoopPattern> getEntityClass() {
        return HoopPattern.class;
    }

    @Override
    protected String getEntityName() {
        return "hoop pattern";
    }
}
