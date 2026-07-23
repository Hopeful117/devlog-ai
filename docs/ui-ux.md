# Developer OS – Workspace UI/UX Specification (V1)

## Purpose

This document defines the first user experience of the Developer OS Workspace.

It is **not** a technical implementation document.

Its purpose is to provide a clear product vision that can be implemented consistently by coding agents while remaining aligned with the long-term Developer OS architecture.

---

# Product Philosophy

The Workspace is the product.

DevLog AI, Codeglyphe and future agents are **modules** of the Workspace.

Users should never feel that they are "using DevLog".

Instead, they work on their software projects while intelligent modules continuously assist them.

The UI must therefore be **project-centric**, not AI-centric.

---

# Design Principles

The interface should follow these principles:

* Project-first navigation.
* Dashboard-oriented workflow.
* AI works in the background.
* Human remains in control.
* Minimal navigation depth.
* Progressive disclosure of technical details.
* Modern professional dashboard aesthetic.
* Consistent with the visual language already used by Trading OS.

The Workspace should feel closer to an operating system than to a CRUD application.

---

# Primary Navigation

The global navigation should remain intentionally small.

```
Developer OS

Projects
Notifications
Settings
```

Projects are the central entry point.

Everything else revolves around projects.

---

# Project Navigation

Opening a project displays its dedicated workspace.

```
Project

Cockpit
Activity
Knowledge
Documentation
Settings
```

Future modules may introduce additional pages without changing the overall navigation philosophy.

---

# Cockpit

The Cockpit is the primary landing page.

It answers one question:

> "What should I know before I start working on this project today?"

The Cockpit should not expose internal implementation concepts such as Tasks, Proposals or Insights.

Instead it provides an operational overview.

---

## Header

Display:

* project name;
* current branch;
* latest synchronization time;
* repository status;
* last repository analysis.

---

## Project Health

Top cards should summarize the project.

Examples include:

* Repository Health
* Knowledge Coverage
* Documentation Coverage
* Roadmap Progress

These indicators should evolve as the project evolves.

---

## Activity

Display the most recent project events.

Examples:

* commits analysed;
* ADR completed;
* documentation generated;
* roadmap updated;
* architecture analysed.

Activity is chronological.

---

## Pending Work

The Workspace should suggest work rather than expose internal objects.

Examples:

* review new findings;
* validate generated documentation;
* review architecture analysis;
* update roadmap.

Suggestions remain optional.

The developer decides.

---

## Agent Status

The Workspace should expose agent activity without overwhelming the user.

Examples:

* Repository Agent
* History Agent
* Documentation Agent
* Codeglyphe

Possible states:

* Running
* Idle
* Waiting
* Completed

Agent execution should communicate that the Workspace continuously maintains project knowledge.

---

## Recent Analyses

Display completed analyses.

Examples:

* Project State
* Architecture Review
* README Generation
* Release Summary

Each analysis provides:

* execution date;
* status;
* quick access to results.

---

## Documentation

Display the latest generated project documentation.

Examples:

* README
* Architecture Overview
* ADR
* Articles
* Release Notes

Documentation should appear as project assets rather than AI outputs.

---

# Activity Page

The Activity page represents the living history of the project.

It combines:

* Git history;
* AI analyses;
* validated knowledge;
* documentation generation;
* roadmap events;
* architectural milestones.

Events should appear in chronological order.

Future filtering should support:

* commits;
* ADR;
* documentation;
* AI analyses;
* roadmap.

---

# Knowledge Page

Knowledge represents trusted project understanding.

The page should expose project knowledge rather than implementation objects.

Possible categories:

* Architecture
* Business
* Infrastructure
* Security
* AI
* Development

Navigation should remain knowledge-oriented.

---

# Documentation Page

Documentation groups every generated project artifact.

Examples include:

* README
* Architecture documents
* Technical articles
* Release notes

Generation history may remain accessible but should not dominate the interface.

---

# Internal Workflow

Internal concepts remain unchanged.

```
Analysis

↓

Proposal

↓

Human Validation

↓

Knowledge

↓

Documentation
```

However these concepts should remain implementation details.

The user-facing workflow becomes:

```
Launch Analysis

↓

Results

↓

Validate

↓

Knowledge Updated

↓

Generate Documentation
```

The UI should communicate outcomes rather than implementation steps.

---

# Notifications

Notifications represent meaningful project events.

Examples:

* New repository analysis completed.
* Documentation ready for review.
* Architecture changes detected.
* Roadmap milestone completed.
* Knowledge updated.

Notifications should summarize value rather than technical execution.

---

# Future Evolution

The Cockpit is intentionally designed as the foundation of the Developer Operating System.

Future modules such as Codeglyphe and additional intelligent agents should enrich the Cockpit rather than introduce independent user interfaces.

The Workspace remains the single entry point.

---

# Implementation Guidelines

The implementation should:

* preserve the current backend architecture;
* reuse existing Angular standards;
* preserve existing authentication;
* follow the existing professional dark dashboard style;
* use reusable dashboard cards;
* remain responsive;
* avoid exposing backend implementation concepts whenever possible.

The implementation should favor gradual migration rather than a complete rewrite.

Existing functionality should continue to operate throughout the transition.

---

# Success Criteria

The implementation will be considered successful when a developer can open a project and immediately understand:

* current project health;
* recent activity;
* pending work;
* documentation status;
* knowledge status;
* ongoing agent activity;

without needing to understand the internal DevLog AI architecture.
