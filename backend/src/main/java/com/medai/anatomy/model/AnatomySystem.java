package com.medai.anatomy.model;

/**
 * Body systems the anatomy layer can currently place a finding in.
 *
 * <p>Only systems reachable from the anatomy catalog are modelled. This is deliberately not a
 * complete list of human anatomical systems.
 */
public enum AnatomySystem {
    SKELETAL,
    NERVOUS,
    RESPIRATORY,
    URINARY,
    OTHER
}
