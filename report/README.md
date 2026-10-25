# Technical Project Report - Distributed Library Management System

This directory contains the complete LaTeX source code for the comprehensive technical project report on the **Distributed Library Management System**.

## Document Overview
- **Title**: Advanced Distributed Library Management System: Design, Implementation, and Distributed Systems Analysis
- **Author**: Engineering Team (Members 1, 2, 3, 4)
- **Format**: LaTeX `report` class (A4 paper, 12pt font)
- **Includes**:
  - Full system architecture & data flow diagrams (rendered natively with `tikz`)
  - Complete data models and MongoDB schema specifications
  - Member 1: Core Backend, UI, Snowflake & TSID Distributed ID Generators, JWT Security, Atomic Inventory Concurrency
  - Member 2: Redis Cache-Aside Pattern, Trie-based Prefix Autocomplete Search, Asynchronous Web Crawler
  - Member 3: Consistent Hashing Ring (MurmurHash3, virtual nodes, node migration bounds, MongoDB sharded cluster architecture)
  - Member 4: Distributed Token-Bucket Rate Limiter (Redis Lua scripts, HTTP standard headers, in-memory fallback), Base62 URL Shortener with dual-tier caching
  - Full REST API specifications (13 controllers, 40+ endpoints)
  - Configuration files (`application.properties`, `docker-compose.yml`, sharding scripts)
  - Testing suite overview (11 test suites covering all modules)
  - Deployment, execution, and verification guide

## Compilation Instructions

### Option 1: Overleaf (Recommended - Zero Setup)
1. Go to [https://www.overleaf.com/](https://www.overleaf.com/).
2. Click **New Project** > **Upload Project**.
3. Select the provided `library_management_system_report_latex.zip` file.
4. Set compiler to **pdfLaTeX** (default in Overleaf).
5. Click **Recompile** to view and download the PDF.

### Option 2: Local TeX Distribution (TeX Live / MiKTeX / MacTeX)
Run `pdflatex` twice to resolve cross-references and Table of Contents:
```bash
pdflatex main.tex
pdflatex main.tex
```

Or using `latexmk`:
```bash
latexmk -pdf main.tex
```

### Required LaTeX Packages (All standard in TeX Live / MiKTeX / Overleaf):
- `geometry`, `graphicx`, `hyperref`, `xcolor`
- `listings`, `booktabs`, `longtable`, `tabularx`
- `enumitem`, `amsmath`, `tikz` (`shapes.geometric`, `arrows.meta`, `positioning`, `calc`)
- `fancyhdr`, `titlesec`, `tocloft`, `caption`, `float`
