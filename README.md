# Palm Vein Comparison – 1:1 & 1:N Demo  
### BioPay Internal Architecture & SDK Integration (Public-Safe Version)

![Java](https://img.shields.io/badge/Java-17-orange)
![SpringBoot](https://img.shields.io/badge/Spring%20Boot-3.0-brightgreen)
![WebSocket](https://img.shields.io/badge/WebSocket-enabled-blue)
![Architecture](https://img.shields.io/badge/Architecture-System%20Design-purple)
![Status](https://img.shields.io/badge/Maintained-Yes-success)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)


This repository contains the **application-side code**, architecture, and integration logic I worked on while designing and testing components of a palm vein recognition system.  

⚠ **Important:**  
All vendor-specific SDK binaries (`.dll`, `.so`, `.bin`) and proprietary libraries have been **removed** due to licensing and confidentiality requirements.  
Only the integration logic, server-side application code, and architecture documentation are shared here.

---

##  Overview

This project demonstrates:

- How a backend service communicates with a biometric SDK  
- Architecture planning for a palm vein recognition device  
- Data workflow for capturing → preprocessing → matching  
- Mapping layers, controllers, and domain models  
- WebSocket-based camera communication  
- Example endpoints for 1:1 and 1:N comparison flows

Even though the vendor SDK files are removed, the source code reflects how the system interfaces with native biometric algorithms.

---

## 🏗 Project Structure
src/
├── main/
│ ├── java/org/example/javacamerserver/
│ │ ├── controller/ # REST Controllers
│ │ ├── domain/ # Data Models (FeatureData, PalmComparisonResult, etc.)
│ │ ├── mapper/ # DTO ↔ Entity Mappers
│ │ ├── webSocket/ # Realtime camera communication
│ │ ├── xrCamer/ # Camera driver integration logic
│ │ ├── utils/ config/ # Helpers & configuration
│ │ └── JavaCamerServerApplication.java
│ └── resources/
│ ├── application.yml # Server config
│ └── sdk/README.md # Vendor binaries removed
├── test/
├── pom.xml
└── README.md (this file)


---

## 🔍 Features Included in This Public Version

### ✔ 1. Controller Layer  
Endpoints for triggering comparison, sending camera commands, and receiving match results.

### ✔ 2. Domain Models  
Structured data objects used during the matching process:
- `FeatureData`
- `PalmComparisonResult`
- `TdxConfig`
- `TdxPalm`

### ✔ 3. Mapper Layer  
Converts low-level SDK output into clean Java response models.

### ✔ 4. WebSocket Camera Interface  
Handles:
- Frame streaming  
- Capture commands  
- Device handshake  
- Status monitoring  

### ✔ 5. XR Camera Integration Logic  
Camera state handling and driver-level connections (minus proprietary binaries).

---

## 🚫 SDK Binaries Removed

The following files are **intentionally excluded**:

gmssl.dll
libusb-1.0.dll
SonixCamera.dll
XRCommonVeinAlgAPI.dll
reg_img.bin


A placeholder is provided at:


with this message:

> “SDK binaries removed due to licensing restrictions.”

---

## 🧪 How to Run (Without SDK)

This project **cannot run full comparison logic** without the vendor SDK because matching is performed inside native DLL files.

However, you can still run:

✔ API layer  
✔ Controllers  
✔ WebSocket server  
✔ Camera command mocks  
✔ Logging  
✔ Domain model flow  
✔ Architecture tests  

To start the server:

```bash
mvn spring-boot:run

 Technologies Used

Java 17

Spring Boot

WebSocket

Maven

Native SDK interfacing

Hardware architecture design

Notes

This repository is intended to showcase:

My work in biometric device architecture

Integration design

Clean server-side project structure

Ability to work with advanced SDKs and hardware systems

Understanding of 1:1 and 1:N matching flows

All confidential components are removed for safety.

License

This project is shared for educational and portfolio purposes only.
Commercial use or reverse engineering of biometric logic is prohibited. 

Author

Neo Gonsalves

LinkedIn: https://www.linkedin.com/in/neo-gonsalves-b86a4b283/

GitHub: https://github.com/NeoGonsalves

“Vendor SDK binaries removed due to licensing restrictions.” (Already done)

This makes your repo:

Professional

Legally safe

Usable

Recruiter-friendly
