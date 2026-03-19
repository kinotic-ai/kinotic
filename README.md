# 🌀 Kinotic

### **Vibe Coding at Scale**

Kinotic is a next-generation Operating System for the Cloud. Just as traditional OSs (like Linux or Windows) abstracted away the complexity of hardware, **Kinotic OS abstracts away the complexity of modern cloud-based applications.** We believe that by removing the friction of infrastructure, networking, and deployment, we enable AI to rapidly prototype and build enterprise-grade applications that can operate at internet scale.



---

## 🚀 The Vision

Modern cloud development is bogged down by "plumbing"—IAM roles, VPCs, CI/CD pipelines, and service meshes. Kinotic OS handles this underlying complexity, allowing developers (and AI agents) to focus on the **vibe**: the core logic, user experience, and business value.

* **Rapid Prototyping:** Move from idea to a scalable Kubernetes deployment in minutes.
* **Enterprise Ready:** Built-in RBAC, SBOM support, and observability from day one.
* **Internet Scale:** Designed to handle massive throughput with automated dependency management and Firecracker VM isolation.

---

## 🏗️ Core Components

| Component | Description |
| :--- | :--- |
| **Kinotic OS** | The complete system, deployable to any Kubernetes cluster or forward-deployed to customer environments. |
| **Kinotic OS Cloud** | Our managed SaaS offering for teams who want to start building immediately without managing infrastructure. |
| **Kinotic CLI** | The developer's primary tool for scaffolding, local development, and rapid cloud iteration. |

---

## 🛠️ Feature Set

### **1. Application Scaffolding & Artifacts**
Kinotic organizes work into **Applications** (logical containers) and **Projects** (functional units). Projects contribute specific **Artifacts**:

* **Microservices:** Continuous code running in pods/VMs with automatic proxy generation.
* **Persistence:** Domain models, CRUD services, and named queries.
* **Batch Jobs:** Scheduled code execution.
* **UI Components:** Atomic building blocks for shared use.
* **Frontends:** Complete UI that can be deployed as a static site.

### **2. Service Directory**
A centralized registry providing a source of truth for all services within an application:
* Function definitions and input schemas.
* RBAC policies required for access.
* Metadata for AI-agent discovery and documentation.

### **3. Security & Identity (Powered by Cedar)**
We use the **Cedar Policy Language** for fine-grained, high-performance authorization across three hierarchies:
* **System Level:** For Kinotic OS administrators.
* **Organization Level:** For development teams.
* **Application Level:** For end-users and machine-to-machine connections.
* *Support for OIDC (Google, GitHub, etc.) and native email/password management.*

### **4. Intelligent CI/CD & Dependency Management**
* **Vibe-to-Code Workflow:** Feature-based development where every branch gets its own development pod.
* **Firecracker Isolation:** Builds are executed in isolated Firecracker VMs for speed and security.
* **Promotion Pipeline:** Automated flow from Dev → Staging (Vulnerability scanning & Migration tests) → Production.



---

## 📊 Observability & LLM Insights

Kinotic provides deep visibility into your application's health and AI interactions:
* **Metrics:** Real-time CPU, Memory, and Data Throughput.
* **Traces & Spans:** Drill down from high-level overviews to detailed execution logs.
* **LLM Observability:** Trace user interactions with LLMs, track token utilization for cost analysis, and index chats via **Loki**.
* **Audit Logs:** Detailed history of logins and configuration changes.

---

## 💰 The Kinotic Marketplace
Kinotic OS isn't just a development platform; it is a **SaaS Launchpad**. Developers can build full-scale SaaS applications and instantly monetize them by deploying to **Kinotic OS Cloud**.

* **Zero-Config Monetization:** Kinotic acts as the Merchant of Record, handling the complexities of global payments and subscription lifecycles.
* **Flexible Revenue Models:** Set your pricing based on the metrics that matter most—whether that’s a flat rate per user, data throughput, storage, or custom application-specific usage.
* **Enterprise Availability:** Deploying to the Kinotic OS cloud makes your application instantly accessible to users on a production-hardened foundation that is ready to grow and scale with you.

---

## 🛣️ Roadmap

### **Post-MVP Goals**
* **Automated QA:** Integrated testing suites for the post-staging phase.
* **Zero-Downtime Migration:** Seamlessly move from Kinotic Cloud to Self-Hosted environments.
* **Real-time Co-browsing:** Built-in support for Organizations to assist their users directly in the browser.

---

## 💻 Technical Stack

* **Orchestration:** Kinotic OS / Kubernetes
* **Policy Engine:** [Cedar](https://www.cedarpolicy.com/)
* **Runtimes:** Firecracker VM, Bun
* **Database:** Postgres (Hibernate Reactive)
* **Logging:** Grafana Loki
* **Payments:** Stripe Connect

---

## 🤝 Contributing
We welcome contributions, just create PR. 

---

> **Kinotic OS:** Stop building infrastructure. Start shipping the vision..