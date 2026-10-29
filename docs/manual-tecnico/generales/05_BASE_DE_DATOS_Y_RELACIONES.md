# Base de datos y relaciones principales

La persistencia usa PostgreSQL con PostGIS. Flyway versiona las modificaciones del esquema y Hibernate valida las entidades contra la estructura existente; la aplicación no crea tablas automáticamente. Las relaciones centrales conectan usuarios y cursos con puentes, inspecciones, revisiones y resultados técnicos.

```mermaid
erDiagram
    ROL ||--o{ USUARIO : asigna
    DEPARTAMENTO ||--o{ MUNICIPIO : contiene
    MUNICIPIO ||--o{ PUENTE : ubica
    ASIGNATURA ||--o{ CURSO : define
    USUARIO ||--o{ CURSO : imparte
    CURSO ||--o{ CURSO_ESTUDIANTE : inscribe
    USUARIO ||--o{ CURSO_ESTUDIANTE : participa
    CURSO_ESTUDIANTE ||--o{ ASIGNACION_PUENTE : recibe
    PUENTE ||--o{ ASIGNACION_PUENTE : asignado
    PUENTE ||--o{ INSPECCION : inspeccionado
    USUARIO ||--o{ INSPECCION : autor
    VERSION_FORMULARIO ||--o{ INSPECCION : estructura
    INSPECCION ||--o{ REVISION : enviada
    USUARIO ||--o{ REVISION : revisa
    INSPECCION ||--o{ RESULTADO_IC : calcula

    ROL {
        uuid id PK
        string nombre
    }
    USUARIO {
        uuid id PK
        uuid rol_id FK
        string email
    }
    DEPARTAMENTO {
        uuid id PK
        string nombre
    }
    MUNICIPIO {
        uuid id PK
        uuid departamento_id FK
        string nombre
    }
    PUENTE {
        uuid id PK
        uuid municipio_id FK
        string codigo
        geography ubicacion
    }
    ASIGNATURA {
        uuid id PK
        string codigo
    }
    CURSO {
        uuid id PK
        uuid asignatura_id FK
        uuid catedratico_id FK
        string periodo
    }
    CURSO_ESTUDIANTE {
        uuid id PK
        uuid curso_id FK
        uuid estudiante_id FK
    }
    ASIGNACION_PUENTE {
        uuid id PK
        uuid curso_estudiante_id FK
        uuid puente_id FK
    }
    VERSION_FORMULARIO {
        uuid id PK
        string codigo
        jsonb esquema
    }
    INSPECCION {
        uuid id PK
        uuid puente_id FK
        uuid autor_id FK
        uuid version_formulario_id FK
        jsonb datos
    }
    REVISION {
        uuid id PK
        uuid inspeccion_id FK
        uuid revisor_id FK
        int numero_ciclo
    }
    RESULTADO_IC {
        uuid id PK
        uuid inspeccion_id FK
        decimal indice_calculado
    }
```

La relación académica entre cursos, estudiantes y puentes se representa con entidades de asociación, lo que permite conservar sección, estado de inscripción y revocación de asignaciones. Una inspección conserva referencias a su puente, autor y versión de formulario. Puede generar varios resultados IC porque se registran publicaciones y recálculos; los ciclos de revisión se distinguen por número. El diagrama omite catálogos, archivos, bitácoras e historiales para facilitar su lectura.

Los datos espaciales de puentes se almacenan con PostGIS. El contenido variable de formularios y algunos desgloses se guarda en JSONB, mientras las relaciones y restricciones de integridad se expresan con claves foráneas, índices y restricciones de base de datos.
