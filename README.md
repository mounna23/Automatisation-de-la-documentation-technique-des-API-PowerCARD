#  API Generator – Spring Boot REST API

This project automates the generation of API documentation and related assets from structured Excel and Confluence files 
It is designed for API teams to accelerate documentation creation and maintain consistency across technical deliverables.  

The application is built as a **Spring Boot REST API**, exposing endpoints that allow users to upload Excel/JSON files and generate OpenAPI specifications, DTOs, SQL scripts, and presentation assets.  

---

## Key Deliverables

- **OpenAPI YAML files** (Request, Response, Aggregates)  
- **JSON files** (module descriptor, nominal case)  
- **SQL scripts** and **Java DTOs**  
- **PowerPoint presentations** from a template  
- **PNG diagrams/screenshots** from generated PPT slides  

---

##  Features

- Excel-driven API specification parsing  
- Automatic OpenAPI 3.0 schema generation   
- PPT generation with placeholder replacement  
- PPT to PNG export for visual documentation  
- Supports API filtering and debug logging  
- Customizable templates for YAML, PPT, and static assets  

---

## Input Sources

The generator supports multiple sources of input:

- **Excel files (.xlsx, .xls)** –  Already implemented and fully functional  
- **Confluence pages** –  Work in progress (planned integration, still under modification)  

This flexibility allows API teams to choose their preferred source of truth while keeping documentation generation consistent.

---

##  Requirements

- Java 8 or higher  
- Maven 3.6+  
- Spring Boot 3.x  
- Apache POI – For processing Excel files  
- Jackson – For JSON serialization
  
---

##  Project Architecture (Spring Boot layered design)

This project follows a **classic Spring Boot layered architecture** for clarity and maintainability:

- **`controller/`** → Exposes REST endpoints (e.g. `GeneratorController`)  
- **`service/`** → Contains the business logic (`GeneratorService`, `YamlFromJsonService`, etc.)  
- **`model/`** → Data structures used for API input/output , in this project we only have one :FieldNode
  <img width="563" height="434" alt="image" src="https://github.com/user-attachments/assets/d9e8bd76-02ee-4219-ae73-1ff3e3ad7a82" />

---
##  Specification 

There are two approaches for generating the aggregate file, each illustrated with examples.
This explains why the Service layer contains two separate classes:

    - **`YamlFromJsonService/`** → The simpler approach. It generates examples that include the full definition of objects inside a list, mentioning each attribute explicitly (including their names).

    - **`YamlFromJsonListService/`** → The alternative approach. It generates examples by showing each object of the list, but without redefining the full structure of the object inside.

##  REST API Endpoints

Base URL: `http://localhost:8080/api/generator`

## Testing 
### Generate Documentation from File

Endpoint:
POST /api/generator/generate

Description:
Uploads an Excel (.xlsx, .xls) or Confluence Page .

Parameters:

file → The Excel/Confluence file containing API specifications.
apiRestrictedList (optional) → Restrict processing to specific APIs (comma-separated).
outputPath  → Custom folder where generated files will be stored.

### Process a JSON into YAML

Endpoint:
POST /api/generator/process

Description:
Transforms a JSON file into a structured YAML file.

Parameters:

jsonPath → Path of the JSON input file.
yamlPath → Path of the YAML template file.
outputPath → Name of the generated YAML (default: aggregate_filled.yaml).

### Process a List of JSON Objects into YAML

Endpoint:
POST /api/generator/processlist

Description:
Processes a list of JSON objects and writes them into a YAML file.

Parameters:

jsonPath  → Path of the JSON input file.
yamlPath  → Path of the YAML template file.
outputPath → Name of the generated YAML (default: aggregate_filled_list.yaml).

## Author

Mouna Ed-daoudi , Douaa Elhaddoudi — Internship at HPS

