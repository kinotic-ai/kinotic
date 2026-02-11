// Enums for all string unions
export enum EducationLevel {
    UNDERGRADUATE = 'undergraduate',
    GRADUATE = 'graduate',
    POSTGRADUATE = 'postgraduate'
}

export enum CertificationType {
    BOARD = 'board',
    SPECIALTY = 'specialty',
    SUBSPECIALTY = 'subspecialty',
    PROFESSIONAL = 'professional'
}

export enum LicenseType {
    MEDICAL = 'medical',
    SPECIALTY = 'specialty',
    PRESCRIPTIVE = 'prescriptive',
    CONTROLLED_SUBSTANCE = 'controlled_substance'
}

export enum PublicationType {
    JOURNAL = 'journal',
    BOOK = 'book',
    CHAPTER = 'chapter',
    ABSTRACT = 'abstract',
    PRESENTATION = 'presentation'
}

export enum MembershipType {
    MEMBER = 'member',
    FELLOW = 'fellow',
    HONORARY = 'honorary',
    STUDENT = 'student'
}

export enum TrainingType {
    RESIDENCY = 'residency',
    FELLOWSHIP = 'fellowship',
    INTERNSHIP = 'internship'
}

export enum Status {
    ACTIVE = 'active',
    EXPIRED = 'expired',
    SUSPENDED = 'suspended',
    REVOKED = 'revoked',
    PENDING = 'pending'
}

export enum SpecializationLevel {
    PRIMARY = 'primary',
    SECONDARY = 'secondary',
    SUBSPECIALTY = 'subspecialty'
}

export enum DuesFrequency {
    ANNUAL = 'annual',
    MONTHLY = 'monthly',
    QUARTERLY = 'quarterly'
}

export enum ResearchType {
    CLINICAL = 'clinical',
    BASIC_SCIENCE = 'basic_science',
    TRANSLATIONAL = 'translational',
    EPIDEMIOLOGICAL = 'epidemiological'
}

export enum TrialPhase {
    PHASE_I = 'I',
    PHASE_II = 'II', 
    PHASE_III = 'III',
    PHASE_IV = 'IV'
}

export enum ActivityType {
    LEADERSHIP = 'leadership',
    SERVICE = 'service',
    RESEARCH = 'research',
    EDUCATION = 'education'
}

// Nested Union: Different types of research activities
export class ClinicalResearch {
    public type: ResearchType = ResearchType.CLINICAL
    public trialPhase?: TrialPhase
    public patientCount!: number
    public outcomes: string[] = []
    public regulatoryApproval?: string
    public funding: Funding[] = []
}

export class BasicScienceResearch {
    public type: ResearchType = ResearchType.BASIC_SCIENCE
    public laboratory!: string
    public techniques: string[] = []
    public publications: string[] = []
    public collaborations: string[] = []
    public equipment: string[] = []
}

export class TranslationalResearch {
    public type: ResearchType = ResearchType.TRANSLATIONAL
    public benchToBedside: boolean = true
    public clinicalApplications: string[] = []
    public industryPartnerships: string[] = []
    public patents: string[] = []
}

export class EpidemiologicalResearch {
    public type: ResearchType = ResearchType.EPIDEMIOLOGICAL
    public populationSize!: number
    public studyDesign!: string
    public statisticalMethods: string[] = []
    public dataSources: string[] = []
}

// Union type for research
export type Research = ClinicalResearch | BasicScienceResearch | TranslationalResearch | EpidemiologicalResearch

// Nested Union: Different types of activities
export class LeadershipActivity { 
    public type: ActivityType = ActivityType.LEADERSHIP
    public position!: string
    public organization!: string
    public teamSize?: number
    public budget?: number
    public achievements: string[] = []
}

export class ServiceActivity { 
    public type: ActivityType = ActivityType.SERVICE
    public service!: string
    public population!: string
    public hours!: number
    public impact!: string
    public recognition?: string
}

export class ResearchActivity {
    public type: ActivityType = ActivityType.RESEARCH
    public research: Research[] = []
    public publications: string[] = []
    public presentations: string[] = []
}

export class EducationActivity {
    public type: ActivityType = ActivityType.EDUCATION
    public role!: string
    public audience!: string
    public hours!: number
    public evaluations: number[] = []
}

// Union type for activities
export type Activity = LeadershipActivity | ServiceActivity | ResearchActivity | EducationActivity

// Nested Union: Different types of funding
export class GrantFunding {
    public type: 'grant' = 'grant'
    public source!: string
    public amount!: number
    public duration!: number
    public role!: string
    public status: Status = Status.ACTIVE
}

export class IndustryFunding {
    public type: 'industry' = 'industry'
    public company!: string
    public amount!: number
    public purpose!: string
    public conflictOfInterest?: string
}

export class FoundationFunding {
    public type: 'foundation' = 'foundation'
    public foundation!: string
    public amount!: number
    public focus!: string
    public requirements: string[] = []
}

// Union type for funding
export type Funding = GrantFunding | IndustryFunding | FoundationFunding

// Nested Union: Different types of evaluations
export class ClinicalEvaluation {
    public type: 'clinical' = 'clinical'
    public patientCare!: number
    public medicalKnowledge!: number
    public practiceBasedLearning!: number
    public interpersonalSkills!: number
    public professionalism!: number
    public systemsBasedPractice!: number
}

export class AcademicEvaluation {
    public type: 'academic' = 'academic'
    public teaching!: number
    public research!: number
    public service!: number
    public publications!: number
    public grants!: number
}

export class AdministrativeEvaluation {
    public type: 'administrative' = 'administrative'
    public leadership!: number
    public management!: number
    public communication!: number
    public decisionMaking!: number
    public strategicPlanning!: number
}

// Union type for evaluations
export type Evaluation = ClinicalEvaluation | AcademicEvaluation | AdministrativeEvaluation

// Education with nested unions
export class Education {
    public level!: EducationLevel
    public degree!: string
    public institution!: string
    public year!: number
    public major!: string
    public minor?: string
    public gpa?: number
    public honors: string[] = []
    public research: Research[] = []  // Nested union
    public activities: Activity[] = []  // Nested union
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}

// Medical Training with nested unions
export class MedicalTraining {
    public type!: TrainingType
    public specialty!: string
    public institution!: string
    public startDate!: Date
    public endDate!: Date
    public supervisor!: string
    public program!: TrainingProgram
    public rotations: Rotation[] = []
    public research: Research[] = []  // Nested union
    public evaluations: Evaluation[] = []  // Nested union
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}

export class TrainingProgram {
    public name!: string
    public accreditation!: string
    public duration!: number
    public description!: string
    public requirements: string[] = []
}

export class Rotation {
    public department!: string
    public duration!: number
    public supervisor!: string
    public responsibilities: string[] = []
    public performance!: string
    public startDate!: Date
    public endDate!: Date
}

// Professional Certifications
export class Certification {       
    public type!: CertificationType
    public name!: string
    public issuingBody!: string
    public yearObtained!: number
    public expiryDate?: Date
    public recertificationRequired: boolean = false
    public recertificationCycle?: number
    public status: Status = Status.ACTIVE
    public requirements!: CertificationRequirements
    public verification!: Verification
    public recertifications: Recertification[] = []
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}

export class CertificationRequirements {
    public education: string[] = []
    public experience: string[] = []
    public examination: string[] = []
    public continuingEducation!: number
    public caseLogs?: number
    public timeInPractice?: number
}

export class Verification {
    public verified: boolean = false
    public verificationDate?: Date
    public verifier?: string
    public notes?: string
    public method?: string
}

export class Recertification {
    public date!: Date
    public requirements: string[] = []
    public status: Status = Status.PENDING
    public notes?: string
    public fees?: number
}

// Medical Licenses
export class License {
    public type!: LicenseType
    public number!: string
    public issuingState!: string
    public issueDate!: Date
    public expiryDate!: Date
    public status: Status = Status.ACTIVE
    public restrictions: string[] = []
    public renewals: LicenseRenewal[] = []
    public disciplinaryActions: DisciplinaryAction[] = []
    public verification!: Verification
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}

export class LicenseRenewal {
    public date!: Date
    public requirements: string[] = []
    public status: Status = Status.PENDING
    public fees!: number
    public continuingEducation!: number
    public applicationDate!: Date
}

export class DisciplinaryAction {
    public date!: Date
    public type!: string
    public description!: string
    public resolution!: string
    public impact!: string
    public reportingBody?: string
}

// Continuing Medical Education
export class ContinuingEducation {
    public year!: number
    public totalCredits!: number
    public activities: CMEActivity[] = []
    public requirements!: CMERequirements
    public compliance!: Compliance
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}

export class CMEActivity {
    public type!: string
    public title!: string
    public credits!: number
    public provider!: string
    public date!: Date
    public certificate?: string
    public category!: string
    public description!: string
    public location?: string
}

export class CMERequirements {
    public totalRequired!: number
    public categories: CMECategory[] = []
    public deadline!: Date
    public gracePeriod?: number
}

export  class CMECategory {
    public category!: string
    public required!: number
    public completed!: number
    public description?: string
}

export class Compliance {
    public compliant: boolean = false   
    public deficit: number = 0
    public gracePeriod?: Date
    public warnings: string[] = []
}

// Academic Publications
export class Publication {
    public type!: PublicationType
    public title!: string
    public authors: string[] = []
    public journal?: string
    public book?: string
    public conference?: string
    public year!: number
    public doi?: string
    public impact!: PublicationImpact
    public details!: PublicationDetails
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}

export class PublicationImpact {
    public citations: number = 0
    public hIndex?: number
    public journalImpactFactor?: number
    public altmetrics?: number
    public downloads?: number
}

export class PublicationDetails {
    public abstract?: string
    public keywords: string[] = []
    public volume?: string
    public issue?: string
    public pages?: string
    public publisher?: string
    public language?: string
}

// Professional Awards and Recognition
export class Award {
    public name!: string
    public year!: number
    public organization!: string
    public description!: string
    public monetary?: number
    public category?: string
    public details!: AwardDetails
    public verification!: Verification
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}

export class AwardDetails {
    public nominationProcess?: string
    public selectionCriteria: string[] = []
    public significance!: string
    public recognition!: string
    public ceremony?: Date
    public location?: string
}

// Professional Memberships
export class Membership {
    public organization!: string
    public type!: MembershipType
    public startDate!: Date
    public endDate?: Date
    public roles: MembershipRole[] = []
    public committees: Committee[] = []
    public details!: MembershipDetails
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}

export class MembershipRole {
    public position!: string
    public startDate!: Date
    public endDate?: Date
    public description!: string
    public responsibilities: string[] = []
}

export class Committee {
    public name!: string
    public role!: string
    public startDate!: Date
    public endDate?: Date
    public meetings: Date[] = []
}

export class MembershipDetails {
    public dues!: Dues
    public benefits: string[] = []
    public requirements: string[] = []
    public newsletter?: boolean
    public conferenceAccess?: boolean
}

export class Dues {
    public amount!: number
    public frequency!: DuesFrequency
    public lastPaid!: Date
    public nextDue!: Date
    public autoRenewal?: boolean
}

// Specializations
export class Specialization {
    public area!: string
    public level!: SpecializationLevel
    public certifications: Certification[] = []
    public experience!: SpecializationExperience
    public verification!: Verification
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}

export class SpecializationExperience {
    public years!: number
    public caseCount!: number
    public procedures: string[] = []
    public patientTypes: string[] = []
    public outcomes: string[] = []
}

// Main Qualification class with nested unions
export class Qualifications {
    public education: Education[] = []
    public training: MedicalTraining[] = []
    public certifications: Certification[] = []
    public licenses: License[] = []
    public continuingEducation: ContinuingEducation[] = []
    public publications: Publication[] = []
    public awards: Award[] = []
    public memberships: Membership[] = []
    public specializations: Specialization[] = []
    public isActive: boolean = true
    public createdAt: Date = new Date()
    public updatedAt: Date = new Date()
}