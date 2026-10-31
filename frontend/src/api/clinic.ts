import { api } from "./client";
export type Service = { id: string; name: string; durationMinutes: number; priceCents: number };
export type Doctor = { id: string; fullName: string; specialty: string; bio: string | null; photoUrl: string | null };
export type Slot = { startsAt: string; endsAt: string };
export type User = { id: string; email: string; role: string; patientId: string | null };
export type Appointment = { id: string; doctorId: string; serviceId: string; doctorName: string; serviceName: string; startsAt: string; endsAt: string; status: string; version: number };
export type AppointmentDetails = Appointment & { specialty: string; history: { fromStatus: string | null; toStatus: string; changedAt: string; changedByRole: string | null }[] };
export type WorkspaceAppointment = { id: string; patientName: string; doctorName: string; serviceName: string; startsAt: string; endsAt: string; status: string; version: number };
export type WorkspaceFilters = { date?: string; query?: string; status?: string; doctorId?: string; serviceId?: string };
export type WorkspaceFilterOptions = { doctors: { id: string; fullName: string }[]; services: { id: string; name: string }[] };
export type AdminDoctor = { id: string; fullName: string; bio: string | null; specialty: string; active: boolean };
export type AdminService = { id: string; name: string; durationMinutes: number; priceCents: number; active: boolean };
export type WorkingHours = { id: string; dayOfWeek: number; startTime: string; endTime: string };
export type TimeOff = { id: string; startsAt: string; endsAt: string; reason: string | null };
export type StaffPatient = { id: string; fullName: string; phone: string | null };
export type Analytics = { total: number; pending: number; confirmed: number; completed: number; cancelled: number; noShow: number; clinicians: number };
export type WaitlistItem = { id: string; serviceId: string; offeredDoctorId: string | null; serviceName: string; doctorName: string | null; requestedDate: string | null; status: string; contactPreference: string; offeredStartsAt: string | null };
export type OperationsWaitlistItem = WaitlistItem & { patientName: string; email: string | null };
export type Specialty = { id: string; name: string };
export type TeamMember = { userId: string; profileId: string; fullName: string; email: string; role: "DOCTOR" | "STAFF"; professionalTitle: string | null; bio: string | null; phone: string | null; specialty: string | null; active: boolean; profileComplete: boolean; lastSignedInAt: string | null; photoUrl: string | null; invitationUrl: string | null };
export const clinicApi = {
  services: () => api<Service[]>("/api/v1/services"),
  doctors: (serviceId: string) => api<Doctor[]>(`/api/v1/doctors?serviceId=${serviceId}`),
  slots: (doctorId: string, serviceId: string, date: string) => api<Slot[]>(`/api/v1/doctors/${doctorId}/slots?serviceId=${serviceId}&date=${date}`),
  register: (body: { email: string; password: string; fullName: string }) => api<User>("/api/v1/auth/register", { method: "POST", body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) => api<User>("/api/v1/auth/login", { method: "POST", body: JSON.stringify(body) }),
  me: () => api<User>("/api/v1/auth/me"),
  csrf: () => api<{ token: string }>("/api/v1/auth/csrf"),
  logout: () => api<void>("/api/v1/auth/logout", { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") } }),
  appointments: () => api<Appointment[]>("/api/v1/appointments"),
  appointmentDetails: (id: string) => api<AppointmentDetails>(`/api/v1/appointments/${id}`),
  cancel: (id: string, version: number) => api<void>(`/api/v1/appointments/${id}/cancel`, { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ version }) }),
  reschedule: (id: string, startsAt: string, version: number) => api<Appointment>(`/api/v1/appointments/${id}/reschedule`, { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ startsAt, version }) }),
  book: (body: { patientId: string; doctorId: string; serviceId: string; startsAt: string }) => api("/api/v1/appointments", { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify(body) }),
  staffPatients: (query: string) => api<StaffPatient[]>(`/api/v1/staff/patients?query=${encodeURIComponent(query)}`),
  createWalkInPatient: (fullName: string, phone: string) => api<string>("/api/v1/staff/patients", { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ fullName, phone }) }),
  workspaceAppointments: (filters?: string | WorkspaceFilters) => {
    const normalized = typeof filters === "string" ? { date: filters } : filters ?? {};
    const params = new URLSearchParams(Object.entries(normalized).filter(([, value]) => Boolean(value)) as [string, string][]);
    return api<WorkspaceAppointment[]>(`/api/v1/workspace/appointments${params.size ? `?${params}` : ""}`);
  },
  workspaceFilterOptions: () => api<WorkspaceFilterOptions>("/api/v1/workspace/filter-options"),
  adminDoctors: () => api<AdminDoctor[]>("/api/v1/admin/doctors"),
  setDoctorActive: (id: string, active: boolean) => api<void>(`/api/v1/admin/doctors/${id}/active`, { method: "PUT", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ active }) }),
  updateDoctor: (id: string, fullName: string, bio: string) => api<void>(`/api/v1/admin/doctors/${id}`, { method: "PUT", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ fullName, bio }) }),
  adminServices: () => api<AdminService[]>("/api/v1/admin/services"),
  setServiceActive: (id: string, active: boolean) => api<void>(`/api/v1/admin/services/${id}/active`, { method: "PUT", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ active }) }),
  createService: (name: string, durationMinutes: number, priceCents: number) => api<string>("/api/v1/admin/services", { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ name, durationMinutes, priceCents }) }),
  updateService: (id: string, name: string, durationMinutes: number, priceCents: number) => api<void>(`/api/v1/admin/services/${id}`, { method: "PUT", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ name, durationMinutes, priceCents }) }),
  workingHours: (doctorId: string) => api<WorkingHours[]>(`/api/v1/admin/doctors/${doctorId}/working-hours`),
  timeOff: (doctorId: string) => api<TimeOff[]>(`/api/v1/admin/doctors/${doctorId}/time-off`),
  replaceWorkingHours: (doctorId: string, dayOfWeek: number, startTime: string, endTime: string) => api<void>(`/api/v1/admin/doctors/${doctorId}/working-hours`, { method: "PUT", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ dayOfWeek, startTime, endTime }) }),
  addTimeOff: (doctorId: string, startsAt: string, endsAt: string, reason: string) => api<string>(`/api/v1/admin/doctors/${doctorId}/time-off`, { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ startsAt, endsAt, reason }) }),
  updateTimeOff: (id: string, startsAt: string, endsAt: string, reason: string) => api<void>(`/api/v1/admin/time-off/${id}`, { method: "PUT", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ startsAt, endsAt, reason }) }),
  deleteTimeOff: (id: string) => api<void>(`/api/v1/admin/time-off/${id}`, { method: "DELETE", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") } }),
  doctorServiceIds: (doctorId: string) => api<string[]>(`/api/v1/admin/doctors/${doctorId}/services`),
  replaceDoctorServices: (doctorId: string, serviceIds: string[]) => api<void>(`/api/v1/admin/doctors/${doctorId}/services`, { method: "PUT", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ serviceIds }) }),
  specialties: () => api<Specialty[]>("/api/v1/admin/specialties"),
  teamMembers: () => api<TeamMember[]>("/api/v1/admin/team-members"),
  myTeamProfile: () => api<TeamMember>("/api/v1/team/profile"),
  updateMyTeamProfile: (bio: string) => api<void>("/api/v1/team/profile", { method: "PUT", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ bio }) }),
  updateMyTeamPhoto: (file: File) => { const form = new FormData(); form.append("file", file); return api<void>("/api/v1/team/profile/photo", { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: form }); },
  removeMyTeamPhoto: () => api<void>("/api/v1/team/profile/photo", { method: "DELETE", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") } }),
  createTeamMember: (body: { fullName: string; email: string; role: "DOCTOR" | "STAFF"; professionalTitle?: string; bio?: string; phone?: string; specialtyId?: string }) => api<TeamMember>("/api/v1/admin/team-members", { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify(body) }),
  completeInvitation: (token: string, password: string) => api<void>("/api/v1/auth/invitations/complete", { method: "POST", body: JSON.stringify({ token, password }) }),
  requestPasswordReset: (email: string) => api<string>("/api/v1/auth/password-reset/request", { method: "POST", body: JSON.stringify({ email }) }),
  completePasswordReset: (token: string, password: string) => api<void>("/api/v1/auth/password-reset/complete", { method: "POST", body: JSON.stringify({ token, password }) }),
  verifyEmail: (token: string) => api<void>("/api/v1/auth/verify-email", { method: "POST", body: JSON.stringify({ token }) }),
  uploadTeamPhoto: (userId: string, file: File) => { const form = new FormData(); form.append("file", file); return api<void>(`/api/v1/admin/team-members/${userId}/photo`, { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: form }); },
  setTeamMemberActive: (userId: string, active: boolean) => api<void>(`/api/v1/admin/team-members/${userId}/active`, { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ active }) }),
  resendTeamInvitation: (userId: string) => api<string>(`/api/v1/admin/team-members/${userId}/resend-invitation`, { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") } }),
  updateTeamMember: (userId: string, body: { fullName: string; email: string; professionalTitle?: string; bio?: string; phone?: string; specialtyId?: string }) => api<void>(`/api/v1/admin/team-members/${userId}`, { method: "PUT", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify(body) }),
  removeTeamPhoto: (userId: string) => api<void>(`/api/v1/admin/team-members/${userId}/photo`, { method: "DELETE", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") } }),
  updateAppointmentStatus: (id: string, version: number, status: string) => api<void>(`/api/v1/appointments/${id}/status`, { method: "PATCH", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ version, status }) }),
  analytics: (from?: string, to?: string) => api<Analytics>(`/api/v1/admin/analytics?${new URLSearchParams({ ...(from ? { from } : {}), ...(to ? { to } : {}) })}`),
  waitlist: () => api<WaitlistItem[]>("/api/v1/waitlist"),
  joinWaitlist: (body: { serviceId: string; doctorId?: string; requestedDate?: string; contactPreference?: string }) => api<string>("/api/v1/waitlist", { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify(body) }),
  cancelWaitlist: (id: string) => api<void>(`/api/v1/waitlist/${id}`, { method: "DELETE", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") } }),
  operationsWaitlist: () => api<OperationsWaitlistItem[]>("/api/v1/waitlist/operations"),
  offerWaitlist: (id: string, startsAt: string) => api<void>(`/api/v1/waitlist/${id}/offer`, { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") }, body: JSON.stringify({ startsAt }) }),
  acceptWaitlistOffer: (id: string) => api<Appointment>(`/api/v1/waitlist/${id}/accept`, { method: "POST", headers: { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") } }),
};
function readCookie(name: string) { return document.cookie.split("; ").find((row) => row.startsWith(`${name}=`))?.split("=")[1] ?? ""; }
