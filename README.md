# Invoice-Generator
Aplicación desarrollada con Spring Boot utilizando Java 17. Se utiliza la librería de JasperReports para la generación de reportes en PDF. 

---

## Índice

1. [🛠️ Tecnologías Utilizadas](#-tecnologías-utilizadas)  
2. [📁 Estructura del Proyecto](#-estructura-del-proyecto)  
3. [🔧 Configuración del Servidor GlassFish](#-configuración-del-servidor-glassfish)  
4. [🚀 Construcción y Despliegue del Proyecto](#-construcción-y-despliegue-del-proyecto)  
5. [👨‍💻 Funcionalidades](#-funcionalidades)  
6. [📌 Notas Adicionales](#-notas-adicionales)

---
## 🛠️ Tecnologías Utilizadas

- Java 17  
- Spring Boot 3.5.4 
- Apache Maven 3.9.6   
- Jasper Reports 7.0.3

---

## 📁 Estructura del Proyecto

![Glassfish NetBeans Config](docs/15-project-structure.png)


---

## 🔧 Configuración del Servidor GlassFish

- Se descarga la versión 5.1.0 del servidor GlassFish desde el sitio oficial de Eclipse:   [https://www.eclipse.org/downloads/download.php?file=/glassfish/glassfish-5.1.0.zip]


![Glassfish source](docs/1-glassfish-source.jpg)  

- En Apache NetBeans IDE 12, se inicia la configuración del servidor GlassFish desde el panel de servicios.

![Glassfish NetBeans Config](docs/2-glassfish-netbeans.jpg)

- Se establece la ubicación local del servidor previamente descargado.

![Glassfish NetBeans Config](docs/3-glassfish-netbeans.jpg)

- Se configura el dominio que utilizará GlassFish. En este caso, se elige un dominio local (`domain1`) para ejecutar las aplicaciones.

![Glassfish NetBeans Config](docs/4-glassfish-netbeans.jpg)

- Se asigna la versión de Java 8 como entorno de ejecución para garantizar compatibilidad con las especificaciones de la aplicación web.

![Glassfish NetBeans Config](docs/5-glassfish-netbeans.jpg)

---

## 🚀 Construcción y Despliegue del Proyecto

- Se crea un nuevo proyecto del tipo **Web Application** en Apache NetBeans.

![Glassfish NetBeans Config](docs/6-glassfish-project.jpg)

- Se define el nombre del proyecto **GlassFishWebApp** y se selecciona la ubicación de guardado.

![Glassfish NetBeans Config](docs/7-glassfish-project.jpg)

- Durante la configuración inicial, se selecciona el servidor GlassFish 5.1 previamente registrado, así como la versión de Java EE correspondiente.

![Glassfish NetBeans Config](docs/8-glassfish-project.jpg)

- Se verifica que el servidor GlassFish esté en ejecución correctamente.

![Glassfish NetBeans Config](docs/9-glassfish-project.jpg)


## 👨‍💻 Funcionalidades

- La aplicación web se despliega correctamente en el servidor GlassFish.

![Glassfish NetBeans Config](docs/10-glassfish-project.jpg)

- Al ejecutar la aplicación, se muestra una pantalla de bienvenida como punto de entrada al sistema.

![Glassfish NetBeans Config](docs/11-glassfish-project.jpg)

- Desde el navegador, se accede a la vista `login.jsp`, que contiene el formulario para autenticación básica, luego de la autenticación se usa la plantilla `welcome.jsp`.

![Glassfish NetBeans Config](docs/12-glassfish-project.jpg)

- Se prueban las credenciales definidas manualmente en el `UserService`, para dos usuarios distintos:

    * 🔐 Usuario Admin

    ![Glassfish NetBeans Config](docs/13-glassfish-project.jpg)


    * 🔐 Usuario Danno

    ![Glassfish NetBeans Config](docs/14-glassfish-project.jpg)

---

## 📌 Notas
- El flujo de autenticación no implementa aún cifrado como mecanismo de seguridad ni almacenamiento en un medio de persistencia.
- Para que la aplicación funcione correctamente, se debe tener en cuenta que:
    - El servidor GlassFish esté ejecutándose y sea compatible con la versión de Java. 
    - Se debe tener instalada Java 1.8
