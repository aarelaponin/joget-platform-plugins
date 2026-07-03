# Tree Menu Plugin for Joget DX

A dynamic hierarchical tree menu plugin for Joget DX 8.x that renders expandable tree navigation in the userview sidebar.

**Version:** 8.1.12
**Platform:** Joget DX 8.x Enterprise Edition
**License:** Apache License 2.0

## Overview

The Tree Menu plugin provides a powerful way to display hierarchical data structures in Joget userviews. It supports three operating modes:

| Mode | Description | Use Case |
|------|-------------|----------|
| **Demo** | Built-in sample data | Testing and evaluation |
| **Metamodel** | Data-driven from config tables | Production deployments with dynamic configuration |
| **Legacy** | Traditional DataListBinder approach | Backward compatibility with existing setups |

## Features

- Hierarchical tree navigation with expand/collapse
- Lazy loading of child nodes for performance
- AJAX-based content loading
- Configurable tree structure via database tables
- Support for both flat and hierarchical data sources
- Integration with Joget forms for content display
- Compatible with Joget Cloud multi-tenant environment

## Quick Start

### Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. Upload `target/tree_menu-8.1.12.jar` to Joget via:
   - **System Settings > Manage Plugins > Upload Plugin**

3. Create a userview and add "Tree Menu" from the Marketplace category

### Demo Mode (Quick Test)

1. Add Tree Menu to your userview
2. Set **Mode** to "Demo (Sample Data)"
3. Save and preview the userview

You'll see a sample tree with:
- Chart of Accounts (hierarchical)
- Enterprise Architecture (categorized data sources)

## Documentation

| Document | Description |
|----------|-------------|
| [User Guide](docs/USER-GUIDE.md) | Administrator's guide for configuration |
| [Developer Guide](docs/DEVELOPER-GUIDE.md) | Technical guide for developers |
| [Design Document](docs/tree-menu-plugin-design.md) | Architecture and design decisions |

## Operating Modes

### Demo Mode

Displays built-in sample data demonstrating two common use cases:
- **Simple Hierarchy**: Chart of Accounts with parent-child relationships
- **Categorized Data**: Enterprise Architecture with folders containing data sources

### Metamodel Mode (Production)

Data-driven tree configuration using a configuration table. Each row defines either:
- **Folder**: A grouping node for organization
- **Data Source**: A connection to a Joget form table

See the [User Guide](docs/USER-GUIDE.md) for configuration table schema.

### Legacy Mode

Uses a DataListBinder to fetch tree data. Compatible with the traditional Joget plugin approach where:
- A binder provides the data
- Column mappings define labels and parent relationships
- An inner menu plugin handles content rendering

## Configuration Table Schema

For Metamodel mode, create a form table with these fields:

| Field | Type | Description |
|-------|------|-------------|
| `c_parent_id` | Text | Parent node ID (empty for root) |
| `c_label` | Text | Display text |
| `c_icon` | Text | Font Awesome icon class |
| `c_sort_order` | Number | Display order |
| `c_node_type` | Text | "folder" or "data" |
| `c_data_table` | Text | Joget table name (for data nodes) |
| `c_form_id` | Text | Form ID for content display |
| `c_label_field` | Text | Column for node labels |
| `c_label_template` | Text | Template like "{c_code} - {c_name}" |
| `c_parent_field` | Text | Column for hierarchy (optional) |
| `c_sort_field` | Text | Column for sorting records |
| `c_filter` | Text | HQL filter condition |

## Requirements

- Joget DX 8.x Enterprise Edition
- Java 8+
- Maven 3.x (for building)

## Build Commands

```bash
# Build the plugin (produces OSGi bundle JAR)
mvn clean package

# Run tests
mvn test

# Build without tests
mvn clean package -DskipTests
```

Output: `target/tree_menu-8.1.12.jar`

## Getting Help

JogetOSS is a community-led team for open source software related to the [Joget](https://www.joget.org) no-code/low-code application platform.
Projects under JogetOSS are community-driven and community-supported.
To obtain support, ask questions, get answers and help others, please participate in the [Community Q&A](https://answers.joget.org/).

## Contributing

This project welcomes contributions and suggestions, please open an issue or create a pull request.

Please note that all interactions fall under our [Code of Conduct](https://github.com/jogetoss/repo-template/blob/main/CODE_OF_CONDUCT.md).

## Changelog

### 8.1.12
- Fixed visual styling for Joget sidebar themes
- Fixed collapse/expand functionality
- Improved builder mode detection for Joget Cloud
- Fixed Jakarta EE servlet compatibility
- Optimized indentation for nested items

### 8.1.0
- Initial release with Demo, Metamodel, and Legacy modes

## Licensing

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

NOTE: This software may depend on other packages that may be licensed under different open source licenses.
