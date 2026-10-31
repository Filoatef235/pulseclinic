import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  QueryClient,
  QueryClientProvider,
  useMutation,
  useQuery,
} from "@tanstack/react-query";
import { useForm, type UseFormRegisterReturn } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  CalendarDays,
  Check,
  Clock3,
  HeartPulse,
  LogIn,
  LogOut,
  Menu,
  ShieldCheck,
  Sparkles,
  UsersRound,
  Stethoscope,
  SlidersHorizontal,
} from "lucide-react";
import { ApiError } from "../api/client";
import {
  clinicApi,
  type AdminDoctor,
  type AdminService,
  type Appointment,
  type AppointmentDetails,
  type Doctor,
  type Service,
  type Slot,
  type StaffPatient,
  type Specialty,
  type TimeOff,
  type TeamMember,
  type User,
  type WorkingHours,
  type WorkspaceAppointment,
  type WorkspaceFilters,
} from "../api/clinic";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 0, refetchOnWindowFocus: true },
  },
});
function updateAppointmentCaches(item: WorkspaceAppointment, status: string) {
  const update = (current: WorkspaceAppointment[] | undefined) =>
    current?.map((entry) =>
      entry.id === item.id
        ? { ...entry, status, version: entry.version + 1 }
        : entry,
    );

  queryClient.setQueriesData<WorkspaceAppointment[]>(
    { queryKey: ["workspace-appointments"] },
    update,
  );
  queryClient.setQueriesData<WorkspaceAppointment[]>(
    { queryKey: ["staff-actions"] },
    update,
  );
  queryClient.setQueriesData<WorkspaceAppointment[]>(
    { queryKey: ["doctor-actions"] },
    update,
  );
}

function refreshAppointmentCaches() {
  void queryClient.invalidateQueries({ queryKey: ["workspace-appointments"] });
  void queryClient.invalidateQueries({ queryKey: ["staff-actions"] });
  void queryClient.invalidateQueries({ queryKey: ["doctor-actions"] });
}
function ProfileAvatar({ name, photoUrl, className = "h-14 w-14" }: { name: string; photoUrl?: string | null; className?: string }) {
  const [failed, setFailed] = useState(false);
  return <div className={`profile-avatar ${className}`} aria-label={`${name} profile photo`}>
    {photoUrl && !failed ? <img src={photoUrl} alt={`${name} profile`} onError={() => setFailed(true)} /> : <span>{name.slice(0, 1).toUpperCase()}</span>}
  </div>;
}
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ClinicExperience />
    </QueryClientProvider>
  );
}
function useScrollReveal<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      element.classList.add("is-visible");
      return;
    }
    if (!("IntersectionObserver" in window)) {
      element.classList.add("is-visible");
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry?.isIntersecting) {
          element.classList.add("is-visible");
          observer.unobserve(element);
        }
      },
      { threshold: 0.14, rootMargin: "0px 0px -8% 0px" },
    );
    observer.observe(element);
    return () => observer.disconnect();
  });
  return ref;
}
function ClinicExperience() {
  const [authOpen, setAuthOpen] = useState<"login" | "register" | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const invitationToken = window.location.pathname.startsWith("/invite/")
    ? window.location.pathname.split("/").filter(Boolean).at(-1) ?? null
    : null;
  const resetToken = window.location.pathname.startsWith("/reset-password/") ? window.location.pathname.split("/").filter(Boolean).at(-1) ?? null : null;
  const verificationToken = window.location.pathname.startsWith("/verify-email/") ? window.location.pathname.split("/").filter(Boolean).at(-1) ?? null : null;
  useEffect(() => {
    clinicApi
      .me()
      .then(setUser)
      .catch(() => undefined);
  }, []);
  const signOut = async () => {
    await clinicApi.csrf();
    await clinicApi.logout();
    setUser(null);
  };
  if (invitationToken) return <InvitationSetup token={invitationToken} />;
  if (resetToken) return <PasswordResetSetup token={resetToken} />;
  if (verificationToken) return <EmailVerification token={verificationToken} />;
  if (user && user.role !== "PATIENT")
    return (
      <>
        <Workspace user={user} onSignOut={() => void signOut()} />
        {user.role === "DOCTOR" && <DoctorAppointmentActions />}
        {(user.role === "STAFF" || user.role === "ADMIN") && (
          <><StaffAppointmentActions /><StaffWalkInBooking /><OperationsWaitlist /></>
        )}
        {user.role === "ADMIN" && (
          <div className="bg-[#f5f7f5] px-5 pb-12 md:px-10">
            <DoctorDirectory />
            <TeamMemberCreator />
            <TeamDirectory />
            <AdminManagement />
            <AdminServiceCreator />
            <AdminSchedulingControls />
            <DoctorServiceAssignments />
            <AdminAnalytics />
          </div>
        )}
      </>
    );
  return (
    <div className="min-h-screen overflow-x-hidden">
      <Announcement />
      <Header
        user={user}
        onAuth={setAuthOpen}
        onSignOut={() => void signOut()}
      />
      <main>
        <Hero
          onStart={() =>
            document
              .getElementById("booking")
              ?.scrollIntoView({ behavior: "smooth" })
          }
        />
        <Booking user={user} onAuth={() => setAuthOpen("login")} />
        <MyAppointments user={user} />
        <ScrollReveal>
          <Trust />
        </ScrollReveal>
      </main>
      <Footer />
      <AuthDialog
        mode={authOpen}
        onClose={() => setAuthOpen(null)}
        onSuccess={(next) => {
          setUser(next);
          setAuthOpen(null);
        }}
      />
    </div>
  );
}
function EmailVerification({ token }: { token: string }) { const verify = useMutation({ mutationFn: () => clinicApi.verifyEmail(token) }); return <main className="flex min-h-screen items-center justify-center bg-[#f5f7f5] p-5"><section className="w-full max-w-md rounded-[1.5rem] bg-white p-7"><p className="eyebrow">PulseClinic</p><h1 className="mt-3 font-serif text-3xl text-[#143d3c]">Verify your email</h1><p className="mt-2 text-sm text-slate-500">Confirm your email address to complete your account setup.</p><button className="btn-primary mt-6" disabled={verify.isPending || verify.isSuccess} onClick={() => verify.mutate()}>{verify.isSuccess ? "Email verified" : "Verify email"}</button>{verify.isError && <p className="mt-3 text-sm text-rose-700">This verification link is invalid or expired.</p>}{verify.isSuccess && <a className="btn-quiet mt-4 inline-block" href="/">Go to sign in</a>}</section></main>; }
function PasswordResetSetup({ token }: { token: string }) { const [password,setPassword]=useState(""); const [confirm,setConfirm]=useState(""); const reset=useMutation({mutationFn:()=>clinicApi.completePasswordReset(token,password)}); const valid=password.length>=12&&/[A-Z]/.test(password)&&/[a-z]/.test(password)&&/\d/.test(password)&&/[^A-Za-z0-9]/.test(password)&&password===confirm; return <main className="flex min-h-screen items-center justify-center bg-[#f5f7f5] p-5"><section className="w-full max-w-md rounded-[1.5rem] bg-white p-7"><p className="eyebrow">PulseClinic</p><h1 className="mt-3 font-serif text-3xl text-[#143d3c]">Choose a new password</h1><input className="field mt-5" type="password" placeholder="New password" value={password} onChange={event=>setPassword(event.target.value)} /><input className="field mt-3" type="password" placeholder="Confirm password" value={confirm} onChange={event=>setConfirm(event.target.value)} /><button className="btn-primary mt-5" disabled={!valid||reset.isPending||reset.isSuccess} onClick={()=>reset.mutate()}>{reset.isSuccess?"Password updated":"Set new password"}</button>{reset.isError&&<p className="mt-3 text-sm text-rose-700">This reset link is invalid or expired.</p>}{reset.isSuccess&&<a className="btn-quiet mt-4 inline-block" href="/">Go to sign in</a>}</section></main>; }
function InvitationSetup({ token }: { token: string }) {
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const complete = useMutation({
    mutationFn: () => clinicApi.completeInvitation(token, password),
    onSuccess: () => setMessage("Your password is ready. You can now sign in."),
    onError: (error) =>
      setMessage(
        error instanceof ApiError
          ? error.message
          : "We could not set your password. Please request a new invitation.",
      ),
  });
  const valid =
    password.length >= 12 &&
    /[A-Z]/.test(password) &&
    /[a-z]/.test(password) &&
    /\d/.test(password) &&
    /[^A-Za-z0-9]/.test(password) &&
    password === confirmation;
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#f5f7f5] p-5">
      <section className="w-full max-w-md rounded-[1.5rem] bg-white p-7 shadow-xl shadow-[#143d3c]/10">
        <p className="eyebrow">PulseClinic team</p>
        <h1 className="mt-3 font-serif text-3xl text-[#143d3c]">Set your password</h1>
        <p className="mt-2 text-sm leading-6 text-slate-500">Choose a password with at least 12 characters, upper and lower case letters, a number, and a symbol.</p>
        <label className="mt-6 block text-sm font-medium text-slate-700">New password
          <input className="field mt-2" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} />
        </label>
        <label className="mt-4 block text-sm font-medium text-slate-700">Confirm password
          <input className="field mt-2" type="password" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} />
        </label>
        <button className="btn-primary mt-6 w-full justify-center" disabled={!valid || complete.isPending || Boolean(message?.startsWith("Your password"))} onClick={() => complete.mutate()}>
          {complete.isPending ? "Saving…" : "Set password"}
        </button>
        {message && <p role="status" className="mt-4 text-sm text-[#28584f]">{message}</p>}
        {message?.startsWith("Your password") && <a className="btn-quiet mt-4 inline-block" href="/">Go to sign in</a>}
      </section>
    </main>
  );
}
function ScrollReveal({ children }: { children: ReactNode }) {
  const ref = useScrollReveal<HTMLDivElement>();
  return (
    <div ref={ref} className="scroll-reveal">
      {children}
    </div>
  );
}
function Announcement() {
  return (
    <div className="announcement" aria-label="Clinic information">
      <div className="announcement-track">
        <span>Thoughtful care, made simpler</span>
        <span>•</span>
        <span>Appointments designed around your time</span>
        <span>•</span>
        <span>Personal, private, unhurried</span>
        <span>•</span>
        <span>Thoughtful care, made simpler</span>
        <span>•</span>
        <span>Appointments designed around your time</span>
      </div>
    </div>
  );
}
function Header({
  user,
  onAuth,
  onSignOut,
}: {
  user: User | null;
  onAuth: (mode: "login" | "register") => void;
  onSignOut: () => void;
}) {
  return (
    <header className="sticky top-0 z-20 border-b border-white/70 bg-[#fbfaf7]/85 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-4">
        <a
          className="flex items-center gap-2 font-serif text-xl font-semibold text-[#143d3c]"
          href="#top"
        >
          <HeartPulse className="text-[#5c857c]" />
          PulseClinic
        </a>
        <nav className="hidden gap-7 text-sm text-slate-600 md:flex">
          <a href="#booking">Book care</a>
          <a href="#care">Our approach</a>
        </nav>
        {user ? (
          <div className="flex items-center gap-2">
            <span className="hidden rounded-full bg-[#e5efe9] px-4 py-2 text-sm text-[#28584f] sm:inline">
              Welcome back
            </span>
            <button className="btn-quiet" onClick={onSignOut}>
              <LogOut size={16} className="mr-1 inline" />
              Sign out
            </button>
          </div>
        ) : (
          <div className="flex gap-2">
            <button className="btn-quiet" onClick={() => onAuth("login")}>
              Sign in
            </button>
            <button className="btn-primary" onClick={() => onAuth("register")}>
              Create account
            </button>
          </div>
        )}
        <Menu className="md:hidden" />
      </div>
    </header>
  );
}
function TeamProfileEditor({ profile }: { profile: TeamMember }) {
  const [open, setOpen] = useState(false);
  const [bio, setBio] = useState(profile.bio ?? "");
  const [notice, setNotice] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: async () => { await clinicApi.csrf(); return clinicApi.updateMyTeamProfile(bio); },
    onSuccess: () => { setOpen(false); setNotice("Profile saved."); void queryClient.invalidateQueries({ queryKey: ["my-team-profile"] }); },
    onError: () => setNotice("Your profile could not be saved. Please try again."),
  });
  const photo = useMutation({
    mutationFn: async (file: File) => { await clinicApi.csrf(); return clinicApi.updateMyTeamPhoto(file); },
    onSuccess: () => { setNotice("Photo updated."); void queryClient.invalidateQueries({ queryKey: ["my-team-profile"] }); },
    onError: (cause) => setNotice(cause instanceof ApiError ? cause.message : "Your photo could not be uploaded."),
  });
  const removePhoto = useMutation({ mutationFn: async () => { await clinicApi.csrf(); return clinicApi.removeMyTeamPhoto(); }, onSuccess: () => { setNotice("Photo removed."); void queryClient.invalidateQueries({ queryKey: ["my-team-profile"] }); }, onError: () => setNotice("Your photo could not be removed. Please try again.") });
  if (!open) return <><button className="btn-quiet mb-3" onClick={() => setOpen(true)}>Edit my profile</button>{notice && <p role="status" className="mb-4 text-sm text-[#28584f]">{notice}</p>}</>;
  return <section className="workspace-editor mb-7"><div className="flex items-center justify-between"><h3 className="font-serif text-xl text-[#143d3c]">Edit my profile</h3><button className="btn-quiet" onClick={() => setOpen(false)}>Close</button></div><label className="mt-4 block text-sm font-medium text-slate-700">Biography<textarea className="field mt-2 min-h-24" value={bio} onChange={(event) => setBio(event.target.value)} maxLength={3000} /></label><label className="mt-3 block text-sm font-medium text-slate-700">{profile.photoUrl ? "Replace photo" : "Add a photo"}<input className="field mt-2" type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => { const file = event.target.files?.[0]; if (file) photo.mutate(file); }} /></label><div className="mt-4 flex flex-wrap gap-2"><button className="btn-primary" disabled={save.isPending} onClick={() => save.mutate()}>Save biography</button>{profile.photoUrl && <button className="workspace-action" disabled={removePhoto.isPending} onClick={() => removePhoto.mutate()}>Remove photo</button>}</div>{notice && <p role="status" className="mt-3 text-sm text-[#28584f]">{notice}</p>}</section>;
}
function Workspace({ user, onSignOut }: { user: User; onSignOut: () => void }) {
  const [date, setDate] = useState("");
  const [activeNav, setActiveNav] = useState("schedule");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [doctorId, setDoctorId] = useState("");
  const [serviceId, setServiceId] = useState("");
  const filters: WorkspaceFilters = { date, query, status: statusFilter, doctorId, serviceId };
  const profile = useQuery({
    queryKey: ["my-team-profile", user.id],
    queryFn: clinicApi.myTeamProfile,
    enabled: user.role === "DOCTOR" || user.role === "STAFF",
  });
  const appointments = useQuery({
    queryKey: ["workspace-appointments", filters],
    queryFn: () => clinicApi.workspaceAppointments(filters),
    refetchInterval: 5000,
  });
  const filterOptions = useQuery({
    queryKey: ["workspace-filter-options"],
    queryFn: clinicApi.workspaceFilterOptions,
  });
  const doctors = useQuery({
    queryKey: ["admin-doctors"],
    queryFn: clinicApi.adminDoctors,
    enabled: user.role === "ADMIN",
  });
  const toggleDoctor = useMutation({
    mutationFn: async (doctor: AdminDoctor) => {
      await clinicApi.csrf();
      return clinicApi.setDoctorActive(doctor.id, !doctor.active);
    },
    onMutate: async (doctor) => {
      await queryClient.cancelQueries({ queryKey: ["admin-doctors"] });
      const previous = queryClient.getQueryData<AdminDoctor[]>([
        "admin-doctors",
      ]);
      queryClient.setQueryData<AdminDoctor[]>(["admin-doctors"], (current) =>
        current?.map((item) =>
          item.id === doctor.id ? { ...item, active: !doctor.active } : item,
        ),
      );
      return { previous };
    },
    onError: (_error, _doctor, context) =>
      queryClient.setQueryData(["admin-doctors"], context?.previous),
    onSettled: () =>
      void queryClient.invalidateQueries({ queryKey: ["admin-doctors"] }),
  });
  const status = useMutation({
    mutationFn: async ({
      item,
      next,
    }: {
      item: WorkspaceAppointment;
      next: string;
    }) => {
      await clinicApi.csrf();
      return clinicApi.updateAppointmentStatus(item.id, item.version, next);
    },
    onMutate: ({ item, next }) => updateAppointmentCaches(item, next),
    onSettled: refreshAppointmentCaches,
  });
  const roleLabel =
    user.role === "DOCTOR"
      ? "Clinician workspace"
      : user.role === "STAFF"
        ? "Operations workspace"
        : "Administration workspace";
  const navigateTo = (section: string) => {
    setActiveNav(section);
    document.getElementById(`workspace-${section}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
  };
  return (
    <div className="min-h-screen bg-[#f5f7f5] text-slate-800">
      <div className="mx-auto grid min-h-screen max-w-[1500px] md:grid-cols-[250px_1fr]">
        <aside className="workspace-sidebar">
          <div className="flex items-center gap-2 font-serif text-2xl text-white">
            <HeartPulse className="text-[#a8d1c3]" />
            PulseClinic
          </div>
          <p className="mt-8 text-xs font-semibold uppercase tracking-[.18em] text-[#a8d1c3]">
            {roleLabel}
          </p>
          <nav className="mt-7 grid gap-2 text-sm text-[#dceae3]">
            <button type="button" className={activeNav === "schedule" ? "workspace-nav-active" : ""} aria-current={activeNav === "schedule" ? "page" : undefined} onClick={() => navigateTo("schedule")}>
              <CalendarDays size={17} />
              Today’s schedule
            </button>
            {user.role !== "DOCTOR" && (
              <button type="button" className={activeNav === "appointments" ? "workspace-nav-active" : ""} aria-current={activeNav === "appointments" ? "page" : undefined} onClick={() => navigateTo("appointments")}>
                <UsersRound size={17} />
                Clinic appointments
              </button>
            )}
            {user.role === "ADMIN" && (
              <button type="button" className={activeNav === "team" ? "workspace-nav-active" : ""} aria-current={activeNav === "team" ? "page" : undefined} onClick={() => navigateTo("team")}>
                <SlidersHorizontal size={17} />
                Team management
              </button>
            )}
          </nav>
          <button
            className="mt-auto flex items-center gap-2 text-sm text-[#dceae3] hover:text-white"
            onClick={onSignOut}
          >
            <LogOut size={16} />
            Sign out
          </button>
        </aside>
        <main className="p-5 md:p-10">
          {profile.data && (
            <section className="mb-7 flex flex-wrap items-center gap-4 rounded-[1.5rem] border border-[#dce8e1] bg-white p-5">
              <ProfileAvatar name={profile.data.fullName} photoUrl={profile.data.photoUrl} />
              <div><p className="eyebrow">Your profile</p><h2 className="mt-1 font-serif text-2xl text-[#143d3c]">{profile.data.fullName}</h2><p className="text-sm text-slate-500">{profile.data.professionalTitle || profile.data.specialty || user.role}</p></div>
            </section>
          )}
          {profile.data && <TeamProfileEditor profile={profile.data} />}
          <header className="flex flex-wrap items-end justify-between gap-5">
            <div>
              <p className="eyebrow">PulseClinic operations</p>
              <h1 className="mt-2 font-serif text-4xl text-[#143d3c]">
                A clear day of care.
              </h1>
              <p className="mt-2 text-sm text-slate-500">
                Signed in as {user.email}
              </p>
            </div>
          </header>
          <section id="workspace-schedule" className="workspace-filters mt-7 scroll-mt-6" aria-label="Appointment filters">
            <div className="workspace-filter-heading">
              <div>
                <p className="eyebrow">Find appointments</p>
                <p className="mt-1 text-sm text-slate-500">Search every appointment, or narrow the schedule when you need to.</p>
              </div>
              <button className="btn-quiet" type="button" onClick={() => { setDate(""); setQuery(""); setStatusFilter(""); setDoctorId(""); setServiceId(""); }}>
                Clear filters
              </button>
            </div>
            <div className="workspace-filter-grid mt-4">
              <label>Search<input className="field" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Patient, clinician, or care" /></label>
              {user.role !== "DOCTOR" && <label>Clinician<select className="field" value={doctorId} onChange={(event) => setDoctorId(event.target.value)}><option value="">All clinicians</option>{filterOptions.data?.doctors?.map((doctor) => <option key={doctor.id} value={doctor.id}>{doctor.fullName}</option>)}</select></label>}
              <label>Care<select className="field" value={serviceId} onChange={(event) => setServiceId(event.target.value)}><option value="">All care types</option>{filterOptions.data?.services?.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select></label>
              <label>Status<select className="field" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="">All statuses</option><option value="PENDING">Pending</option><option value="CONFIRMED">Confirmed</option><option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option><option value="NO_SHOW">No-show</option></select></label>
              <label>Date<input className="field" type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>
            </div>
          </section>
          <section id="workspace-appointments" className="mt-9 scroll-mt-6 rounded-[1.5rem] border border-[#dce8e1] bg-white p-6 shadow-[0_18px_48px_-38px_rgba(20,61,60,.8)]">
            <div className="flex items-center justify-between">
              <div>
                <p className="eyebrow">Schedule</p>
                <h2 className="mt-2 font-serif text-2xl text-[#143d3c]">
                  Appointments
                </h2>
              </div>
              <span className="rounded-full bg-[#e8f2ed] px-3 py-1 text-xs font-semibold text-[#28584f]">
                {appointments.data?.length ?? 0} scheduled
              </span>
            </div>
            {appointments.isLoading ? (
              <p className="mt-7 text-slate-500">Loading schedule…</p>
            ) : appointments.data?.length ? (
              <div className="mt-6 overflow-x-auto">
                <table className="workspace-table">
                  <thead>
                    <tr>
                      <th>Date &amp; time</th>
                      <th>Patient</th>
                      <th>Clinician</th>
                      <th>Care</th>
                      <th>Status</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {appointments.data.map((item: WorkspaceAppointment) => (
                      <tr key={item.id}>
                        <td>
                          {new Intl.DateTimeFormat(undefined, {
                            dateStyle: "medium",
                            timeStyle: "short",
                          }).format(new Date(item.startsAt))}
                        </td>
                        <td>{item.patientName}</td>
                        <td>{item.doctorName}</td>
                        <td>{item.serviceName}</td>
                        <td>
                          <span
                            className={`status-badge status-${item.status.toLowerCase()}`}
                          >
                            {item.status.replace("_", " ")}
                          </span>
                        </td>
                        <td>
                          {item.status === "PENDING" &&
                          user.role !== "DOCTOR" ? (
                            <button
                              className="workspace-action"
                              disabled={status.isPending}
                              onClick={() =>
                                status.mutate({ item, next: "CONFIRMED" })
                              }
                            >
                              Confirm
                            </button>
                          ) : item.status === "CONFIRMED" &&
                            user.role === "DOCTOR" ? (
                            <button
                              className="workspace-action"
                              disabled={status.isPending}
                              onClick={() =>
                                status.mutate({ item, next: "COMPLETED" })
                              }
                            >
                              Complete
                            </button>
                          ) : (
                            "—"
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="mt-7 rounded-xl bg-[#f6faf7] p-5 text-sm text-slate-500">
                No appointments match these filters. Clear or change a filter to see more of the schedule.
              </p>
            )}
          </section>
          {user.role === "ADMIN" && (
            <section id="workspace-team" className="mt-7 scroll-mt-6 rounded-[1.5rem] border border-[#dce8e1] bg-white p-6">
              <div className="flex items-center gap-3">
                <Stethoscope className="text-[#5d9185]" />
                <div>
                  <p className="eyebrow">Team management</p>
                  <h2 className="mt-1 font-serif text-2xl text-[#143d3c]">
                    Clinicians
                  </h2>
                </div>
              </div>
              <div className="mt-6 grid gap-3 md:grid-cols-2">
                {doctors.data?.map((doctor) => (
                  <article
                    key={doctor.id}
                    className="flex items-center justify-between rounded-xl border border-[#e1ece6] p-4"
                  >
                    <div>
                      <p className="font-medium text-[#143d3c]">
                        {doctor.fullName}
                      </p>
                      <p className="text-sm text-slate-500">
                        {doctor.specialty}
                      </p>
                    </div>
                    <button
                      className="btn-quiet border border-[#b8cbc3]"
                      disabled={toggleDoctor.isPending}
                      onClick={() => toggleDoctor.mutate(doctor)}
                    >
                      {doctor.active ? "Deactivate" : "Activate"}
                    </button>
                  </article>
                ))}
              </div>
            </section>
          )}
        </main>
      </div>
    </div>
  );
}
function Hero({ onStart }: { onStart: () => void }) {
  return (
    <section
      id="top"
      className="hero-shell relative mx-auto grid max-w-7xl gap-12 px-5 py-20 md:grid-cols-[1.1fr_.9fr] md:py-28"
    >
      <div className="animate-rise">
        <p className="eyebrow">Concierge care, on your schedule</p>
        <h1 className="mt-5 max-w-3xl font-serif text-5xl leading-[1.04] text-[#123f3c] md:text-7xl">
          Healthcare that makes room for{" "}
          <em className="font-normal text-[#6e958b]">you.</em>
        </h1>
        <p className="mt-6 max-w-xl text-lg leading-8 text-slate-600">
          A more considered way to book care: clear choices, trusted clinicians,
          and time that feels like your own.
        </p>
        <div className="mt-9 flex flex-wrap gap-3">
          <button className="btn-primary px-6 py-3.5" onClick={onStart}>
            Find an appointment <CalendarDays size={18} />
          </button>
          <span className="flex items-center gap-2 px-3 text-sm text-slate-500">
            <ShieldCheck size={17} className="text-[#6e958b]" />
            Private & secure
          </span>
        </div>
        <div className="hero-proof mt-12">
          <div>
            <strong>24h</strong>
            <span>cancellation window</span>
          </div>
          <div>
            <strong>2 min</strong>
            <span>to reserve your time</span>
          </div>
        </div>
      </div>
      <div
        className="hero-composition animate-rise-delayed"
        aria-label="Appointment experience preview"
      >
        <div className="hero-halo" />
        <div className="hero-panel">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-500">Your next moment of care</p>
            <Sparkles size={18} className="text-[#c29259]" />
          </div>
          <div className="mt-12">
            <p className="font-serif text-4xl text-[#143d3c]">Made for you.</p>
            <p className="mt-3 max-w-xs text-sm leading-6 text-slate-500">
              A clear path from choosing care to feeling prepared.
            </p>
          </div>
          <div className="mt-8 flex items-center gap-3 border-t border-[#dfe8e2] pt-5">
            <span className="hero-avatar">MH</span>
            <div>
              <p className="text-sm font-medium text-[#143d3c]">
                Dr. Maya Hassan
              </p>
              <p className="text-xs text-slate-500">Family Medicine</p>
            </div>
          </div>
        </div>
        <div className="hero-note">
          <p className="text-xs font-semibold uppercase tracking-[.15em] text-[#5c857c]">
            Your visit
          </p>
          <p className="mt-2 font-serif text-xl text-[#143d3c]">
            A gentler experience
          </p>
          <p className="mt-1 text-sm text-slate-500">
            From booking to follow-up
          </p>
        </div>
        <div className="hero-availability">
          <span className="availability-dot" />
          Appointments available this week
        </div>
      </div>
    </section>
  );
}
function AdminAnalytics() {
  const [from,setFrom]=useState(new Date(Date.now()-29*86400000).toISOString().slice(0,10)); const [to,setTo]=useState(new Date().toISOString().slice(0,10));
  const data=useQuery({queryKey:["analytics",from,to],queryFn:()=>clinicApi.analytics(from,to)});
  const items=data.data?[ ["Bookings",data.data.total],["Pending",data.data.pending],["Confirmed",data.data.confirmed],["Completed",data.data.completed],["Cancelled",data.data.cancelled],["No-show rate",data.data.total?`${Math.round(data.data.noShow/data.data.total*100)}%`:"0%"],["Clinicians",data.data.clinicians] ]:[];
  return <section className="workspace-section"><p className="eyebrow">Clinic analytics</p><h2 className="mt-2 font-serif text-3xl text-[#143d3c]">A useful operational picture</h2><div className="mt-4 flex flex-wrap gap-3"><input className="date-input" type="date" value={from} onChange={e=>setFrom(e.target.value)}/><input className="date-input" type="date" value={to} onChange={e=>setTo(e.target.value)}/></div><div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{items.map(([label,value])=><div key={String(label)} className="workspace-card"><p className="text-sm text-slate-500">{label}</p><p className="mt-2 font-serif text-3xl text-[#143d3c]">{value}</p></div>)}</div></section>;
}
function AdminManagement() {
  const services = useQuery({
    queryKey: ["admin-services"],
    queryFn: clinicApi.adminServices,
    refetchInterval: 5000,
  });
  const [editing, setEditing] = useState<{
    id: string;
    name: string;
    duration: string;
    price: string;
  } | null>(null);
  const serviceToggle = useMutation({
    mutationFn: async (service: { id: string; active: boolean }) => {
      await clinicApi.csrf();
      return clinicApi.setServiceActive(service.id, !service.active);
    },
    onMutate: (service) => {
      queryClient.setQueryData<AdminService[]>(["admin-services"], (current) =>
        current?.map((item) =>
          item.id === service.id ? { ...item, active: !service.active } : item,
        ),
      );
    },
    onSettled: () =>
      void queryClient.invalidateQueries({ queryKey: ["admin-services"] }),
  });
  const saveEdit = useMutation({
    mutationFn: async () => {
      if (!editing) return;
      await clinicApi.csrf();
      return clinicApi.updateService(
        editing.id,
        editing.name,
        Number(editing.duration),
        Math.round(Number(editing.price) * 100),
      );
    },
    onMutate: () => {
      if (!editing) return;
      queryClient.setQueryData<AdminService[]>(["admin-services"], (current) =>
        current?.map((item) =>
          item.id === editing.id
            ? {
                ...item,
                name: editing.name,
                durationMinutes: Number(editing.duration),
                priceCents: Math.round(Number(editing.price) * 100),
              }
            : item,
        ),
      );
    },
    onSuccess: () => setEditing(null),
    onSettled: () =>
      void queryClient.invalidateQueries({ queryKey: ["admin-services"] }),
  });
  return (
    <section className="mt-7 rounded-[1.5rem] border border-[#dce8e1] bg-white p-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">Service management</p>
          <h2 className="mt-2 font-serif text-2xl text-[#143d3c]">
            Care catalogue
          </h2>
        </div>
        <p className="text-sm text-slate-500">Changes update immediately.</p>
      </div>
      {editing && (
        <div className="workspace-editor mt-6">
          <div className="flex items-center justify-between">
            <h3 className="font-serif text-xl text-[#143d3c]">Edit service</h3>
            <button className="btn-quiet" onClick={() => setEditing(null)}>
              Close
            </button>
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-4">
            <input
              className="field mt-0"
              value={editing.name}
              onChange={(event) =>
                setEditing({ ...editing, name: event.target.value })
              }
            />
            <input
              className="field mt-0"
              type="number"
              min="5"
              value={editing.duration}
              onChange={(event) =>
                setEditing({ ...editing, duration: event.target.value })
              }
            />
            <input
              className="field mt-0"
              type="number"
              min="0"
              step="0.01"
              value={editing.price}
              onChange={(event) =>
                setEditing({ ...editing, price: event.target.value })
              }
            />
            <button
              className="btn-primary justify-center"
              disabled={saveEdit.isPending}
              onClick={() => saveEdit.mutate()}
            >
              Save changes
            </button>
          </div>
        </div>
      )}
      <div className="mt-6 grid gap-3 md:grid-cols-2">
        {services.data?.map((service) => (
          <article
            key={service.id}
            className="workspace-card flex items-center justify-between gap-4"
          >
            <div>
              <p className="font-medium text-[#143d3c]">{service.name}</p>
              <p className="text-sm text-slate-500">
                {service.durationMinutes} min · $
                {(service.priceCents / 100).toFixed(2)}
              </p>
            </div>
            <div className="flex gap-2">
              <button
                className="workspace-action"
                onClick={() =>
                  setEditing({
                    id: service.id,
                    name: service.name,
                    duration: String(service.durationMinutes),
                    price: String(service.priceCents / 100),
                  })
                }
              >
                Edit
              </button>
              <button
                className="workspace-action"
                disabled={serviceToggle.isPending}
                onClick={() => serviceToggle.mutate(service)}
              >
                {service.active ? "Disable" : "Enable"}
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
function TeamMemberCreator() {
  const specialties = useQuery({
    queryKey: ["admin-specialties"],
    queryFn: clinicApi.specialties,
  });
  const [role, setRole] = useState<"DOCTOR" | "STAFF">("DOCTOR");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [title, setTitle] = useState("");
  const [bio, setBio] = useState("");
  const [phone, setPhone] = useState("");
  const [specialtyId, setSpecialtyId] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [invitationUrl, setInvitationUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: async () => {
      await clinicApi.csrf();
      const member = await clinicApi.createTeamMember({
        fullName,
        email,
        role,
        professionalTitle: title || undefined,
        bio: bio || undefined,
        phone: phone || undefined,
        specialtyId: role === "DOCTOR" ? specialtyId : undefined,
      });
      if (photo) await clinicApi.uploadTeamPhoto(member.userId, photo);
      return member;
    },
    onSuccess: async (member) => {
      setInvitationUrl(member.invitationUrl);
      setError(null);
      setFullName("");
      setEmail("");
      setTitle("");
      setBio("");
      setPhone("");
      setSpecialtyId("");
      setPhoto(null);
      await queryClient.invalidateQueries({ queryKey: ["admin-doctors"] });
      await queryClient.invalidateQueries({ queryKey: ["team-members"] });
    },
    onError: (cause) =>
      setError(
        cause instanceof ApiError
          ? cause.message
          : "We could not create this team member.",
      ),
  });
  const valid =
    fullName.trim().length > 1 &&
    /^\S+@\S+\.\S+$/.test(email) &&
    (role === "STAFF" || Boolean(specialtyId));
  return (
    <section className="workspace-section">
      <p className="eyebrow">Team invitations</p>
      <h2 className="mt-2 font-serif text-3xl text-[#143d3c]">
        Add a doctor or staff member
      </h2>
      <p className="mt-2 max-w-3xl text-sm text-slate-500">
        The new team member chooses their own password through a single-use
        invitation. Existing patient sign-in is unchanged.
      </p>
      <div className="workspace-card mt-6 max-w-4xl">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-sm font-medium text-slate-700">
            Role
            <select
              className="field mt-2"
              value={role}
              onChange={(event) =>
                setRole(event.target.value as "DOCTOR" | "STAFF")
              }
            >
              <option value="DOCTOR">Doctor</option>
              <option value="STAFF">Staff</option>
            </select>
          </label>
          <label className="text-sm font-medium text-slate-700">
            Full name
            <input
              className="field mt-2"
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
              autoComplete="name"
            />
          </label>
          <label className="text-sm font-medium text-slate-700">
            Work email
            <input
              className="field mt-2"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
            />
          </label>
          <label className="text-sm font-medium text-slate-700">
            Professional title
            <input
              className="field mt-2"
              placeholder={role === "DOCTOR" ? "e.g. Consultant" : "e.g. Care coordinator"}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </label>
          {role === "DOCTOR" && (
            <label className="text-sm font-medium text-slate-700">
              Specialty
              <select
                className="field mt-2"
                value={specialtyId}
                onChange={(event) => setSpecialtyId(event.target.value)}
              >
                <option value="">Choose specialty</option>
                {specialties.data?.map((specialty: Specialty) => (
                  <option key={specialty.id} value={specialty.id}>
                    {specialty.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label className="text-sm font-medium text-slate-700">
            Phone
            <input
              className="field mt-2"
              type="tel"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              autoComplete="tel"
            />
          </label>
        </div>
        <label className="mt-3 block text-sm font-medium text-slate-700">
          Short profile description
          <textarea
            className="field mt-2 min-h-24"
            value={bio}
            onChange={(event) => setBio(event.target.value)}
            maxLength={3000}
          />
        </label>
        <label className="mt-3 block text-sm font-medium text-slate-700">
          Profile photo <span className="font-normal text-slate-500">(JPG, PNG, or WebP; up to 5 MB)</span>
          <input className="field mt-2" type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => setPhoto(event.target.files?.[0] ?? null)} />
        </label>
        <button
          className="btn-primary mt-5"
          disabled={!valid || create.isPending}
          onClick={() => create.mutate()}
        >
          {create.isPending ? "Creating invitation…" : "Create invitation"}
        </button>
        {error && <p role="alert" className="mt-4 text-sm text-rose-700">{error}</p>}
        {invitationUrl && (
          <div className="mt-5 rounded-xl bg-[#e6f3eb] p-4 text-sm text-[#28584f]">
            <p className="font-medium">Invitation created.</p>
            <p className="mt-1">Copy this local setup link now. It expires in 24 hours.</p>
            <code className="mt-2 block break-all rounded bg-white px-3 py-2 text-xs text-slate-700">
              {window.location.origin}{invitationUrl}
            </code>
          </div>
        )}
      </div>
    </section>
  );
}
function TeamDirectory() {
  const members = useQuery({ queryKey: ["team-members"], queryFn: clinicApi.teamMembers });
  const specialties = useQuery({ queryKey: ["admin-specialties"], queryFn: clinicApi.specialties });
  const [invitation, setInvitation] = useState<{ userId: string; url: string } | null>(null);
  const [invitationNotice, setInvitationNotice] = useState<string | null>(null);
  const [editing, setEditing] = useState<TeamMember | null>(null);
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [activeFilter, setActiveFilter] = useState("ALL");
  const [specialtyFilter, setSpecialtyFilter] = useState("ALL");
  const [query, setQuery] = useState("");
  const [photoNotice, setPhotoNotice] = useState<string | null>(null);
  const active = useMutation({
    mutationFn: async (member: TeamMember) => { await clinicApi.csrf(); return clinicApi.setTeamMemberActive(member.userId, !member.active); },
    onMutate: (member) => queryClient.setQueryData<TeamMember[]>(["team-members"], current => current?.map(item => item.userId === member.userId ? { ...item, active: !item.active } : item)),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: ["team-members"] }),
  });
  const resend = useMutation({
    mutationFn: async (userId: string) => { await clinicApi.csrf(); return clinicApi.resendTeamInvitation(userId); },
    onSuccess: (url, userId) => {
      setInvitation({ userId, url });
      setInvitationNotice(null);
    },
    onError: (cause) => setInvitationNotice(cause instanceof ApiError ? cause.message : "The invitation could not be created. Please try again."),
  });
  const save = useMutation({
    mutationFn: async () => { if (!editing) return; await clinicApi.csrf(); return clinicApi.updateTeamMember(editing.userId, { fullName: editing.fullName, email: editing.email, professionalTitle: editing.professionalTitle ?? undefined, bio: editing.bio ?? undefined, phone: editing.phone ?? undefined, specialtyId: editing.role === "DOCTOR" ? specialties.data?.find(item => item.name === editing.specialty)?.id : undefined }); },
    onSuccess: () => { setEditing(null); void queryClient.invalidateQueries({ queryKey: ["team-members"] }); },
  });
  const replacePhoto = useMutation({ mutationFn: async ({ userId, file }: { userId: string; file: File }) => { await clinicApi.csrf(); return clinicApi.uploadTeamPhoto(userId, file); }, onSuccess: () => { setPhotoNotice("Profile photo updated."); void queryClient.invalidateQueries({ queryKey: ["team-members"] }); }, onError: (cause) => setPhotoNotice(cause instanceof ApiError ? cause.message : "The photo could not be uploaded.") });
  const removePhoto = useMutation({ mutationFn: async (userId: string) => { await clinicApi.csrf(); return clinicApi.removeTeamPhoto(userId); }, onSuccess: () => { setPhotoNotice("Profile photo removed."); void queryClient.invalidateQueries({ queryKey: ["team-members"] }); }, onError: () => setPhotoNotice("The photo could not be removed.") });
  const visible = members.data?.filter(member => (roleFilter === "ALL" || member.role === roleFilter) && (activeFilter === "ALL" || String(member.active) === activeFilter) && (specialtyFilter === "ALL" || member.specialty === specialtyFilter) && `${member.fullName} ${member.email} ${member.specialty ?? ""}`.toLowerCase().includes(query.toLowerCase()));
  return (
    <section className="workspace-section">
      <p className="eyebrow">Team directory</p>
      <h2 className="mt-2 font-serif text-3xl text-[#143d3c]">Accounts and invitations</h2>
      <div className="mt-5 flex flex-wrap gap-3"><input className="field mt-0 max-w-sm" placeholder="Search name, email, specialty" value={query} onChange={(event) => setQuery(event.target.value)} /><select className="field mt-0 w-auto" value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}><option value="ALL">All roles</option><option value="DOCTOR">Doctors</option><option value="STAFF">Staff</option></select><select className="field mt-0 w-auto" value={activeFilter} onChange={(event) => setActiveFilter(event.target.value)}><option value="ALL">All account states</option><option value="true">Active</option><option value="false">Inactive</option></select><select className="field mt-0 w-auto" value={specialtyFilter} onChange={(event) => setSpecialtyFilter(event.target.value)}><option value="ALL">All specialties</option>{specialties.data?.map(item => <option key={item.id} value={item.name}>{item.name}</option>)}</select></div>
      {editing && <div className="workspace-editor mt-5"><div className="flex items-center justify-between"><h3 className="font-serif text-xl text-[#143d3c]">Edit {editing.fullName}</h3><button className="btn-quiet" onClick={() => setEditing(null)}>Close</button></div><div className="mt-4 grid gap-3 md:grid-cols-2"><input className="field mt-0" aria-label="Full name" value={editing.fullName} onChange={(event) => setEditing({ ...editing, fullName: event.target.value })} /><input className="field mt-0" aria-label="Email" type="email" value={editing.email} onChange={(event) => setEditing({ ...editing, email: event.target.value })} /><input className="field mt-0" aria-label="Professional title" value={editing.professionalTitle ?? ""} onChange={(event) => setEditing({ ...editing, professionalTitle: event.target.value })} /><input className="field mt-0" aria-label="Phone" value={editing.phone ?? ""} onChange={(event) => setEditing({ ...editing, phone: event.target.value })} />{editing.role === "DOCTOR" && <select className="field mt-0" value={editing.specialty ?? ""} onChange={(event) => setEditing({ ...editing, specialty: event.target.value })}>{specialties.data?.map(item => <option key={item.id} value={item.name}>{item.name}</option>)}</select>}</div><label className="mt-3 block text-sm">Biography<textarea className="field mt-1 min-h-24" value={editing.bio ?? ""} onChange={(event) => setEditing({ ...editing, bio: event.target.value })} /></label><button className="btn-primary mt-4" disabled={save.isPending} onClick={() => save.mutate()}>Save member</button></div>}
      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {visible?.map((member) => (
          <article key={member.userId} className="workspace-card">
            <div className="flex items-center gap-3">
              <ProfileAvatar name={member.fullName} photoUrl={member.photoUrl} className="h-12 w-12" />
              <div><p className="font-medium text-[#143d3c]">{member.fullName}</p><p className="text-sm text-slate-500">{member.role === "DOCTOR" ? member.specialty : member.professionalTitle || "Staff"}</p></div>
            </div>
            <p className="mt-4 text-sm text-slate-500">{member.email}</p>
            <p className="mt-2 text-xs text-slate-500">{member.lastSignedInAt ? `Last signed in ${new Date(member.lastSignedInAt).toLocaleString()}` : "Password setup not completed yet"} · {member.profileComplete ? "Profile complete" : "Profile needs details"}</p>
            <div className="mt-4 flex flex-wrap gap-2"><button className="workspace-action" onClick={() => setEditing(member)}>Edit</button><label className="workspace-action cursor-pointer">{member.photoUrl ? "Replace photo" : "Add photo"}<input className="sr-only" type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => { const file = event.target.files?.[0]; if (file) replacePhoto.mutate({ userId: member.userId, file }); event.currentTarget.value = ""; }} /></label>{member.photoUrl && <button className="workspace-action" disabled={removePhoto.isPending} onClick={() => removePhoto.mutate(member.userId)}>Remove photo</button>}<button className="workspace-action" disabled={active.isPending} onClick={() => active.mutate(member)}>{member.active ? "Deactivate" : "Activate"}</button><button className="workspace-action" disabled={resend.isPending} onClick={() => resend.mutate(member.userId)}>{resend.isPending ? "Creating link…" : "Resend invitation"}</button></div>
            {invitation?.userId === member.userId && <div role="status" className="mt-4 rounded-xl bg-[#e6f3eb] p-3 text-sm text-[#28584f]"><p className="font-medium">New local invitation ready.</p><p className="mt-1">No email is sent in local development. Copy this link and open it in a new browser tab:</p><code className="mt-2 block break-all rounded-lg bg-white/70 p-2 text-xs">{window.location.origin}{invitation.url}</code><button className="workspace-action mt-2" onClick={() => navigator.clipboard.writeText(`${window.location.origin}${invitation.url}`).then(() => setInvitationNotice("Invitation link copied."), () => setInvitationNotice("Copy the link above manually."))}>Copy link</button></div>}
          </article>
        ))}
      </div>
      {invitationNotice && <p role="status" className="mt-4 rounded-xl bg-[#e6f3eb] p-3 text-sm text-[#28584f]">{invitationNotice}</p>}
      {photoNotice && <p role="status" className="mt-4 rounded-xl bg-[#e6f3eb] p-3 text-sm text-[#28584f]">{photoNotice}</p>}
    </section>
  );
}
function DoctorDirectory() {
  const doctors = useQuery({
    queryKey: ["admin-doctors"],
    queryFn: clinicApi.adminDoctors,
    refetchInterval: 5000,
  });
  const [editing, setEditing] = useState<AdminDoctor | null>(null);
  const save = useMutation({
    mutationFn: async () => {
      if (!editing) return;
      await clinicApi.csrf();
      return clinicApi.updateDoctor(
        editing.id,
        editing.fullName,
        editing.bio ?? "",
      );
    },
    onSuccess: async () => {
      setEditing(null);
      await queryClient.refetchQueries({ queryKey: ["admin-doctors"] });
    },
  });
  const active = useMutation({
    mutationFn: async (doctor: AdminDoctor) => {
      await clinicApi.csrf();
      return clinicApi.setDoctorActive(doctor.id, !doctor.active);
    },
    onMutate: async (doctor) => {
      await queryClient.cancelQueries({ queryKey: ["admin-doctors"] });
      const previous = queryClient.getQueryData<AdminDoctor[]>([
        "admin-doctors",
      ]);
      queryClient.setQueryData<AdminDoctor[]>(["admin-doctors"], (current) =>
        current?.map((item) =>
          item.id === doctor.id ? { ...item, active: !doctor.active } : item,
        ),
      );
      return { previous };
    },
    onError: (_error, _doctor, context) =>
      queryClient.setQueryData(["admin-doctors"], context?.previous),
    onSettled: () =>
      void queryClient.invalidateQueries({ queryKey: ["admin-doctors"] }),
  });
  return (
    <section className="workspace-section">
      <div>
        <p className="eyebrow">Clinician management</p>
        <h2 className="mt-2 font-serif text-3xl text-[#143d3c]">
          Your full care team
        </h2>
        <p className="mt-2 text-sm text-slate-500">
          Each profile can be edited directly. Changes appear immediately
          throughout the dashboard.
        </p>
      </div>
      {editing && (
        <div className="workspace-editor mt-6">
          <div className="flex items-center justify-between">
            <h3 className="font-serif text-xl text-[#143d3c]">
              Edit {editing.fullName}
            </h3>
            <button className="btn-quiet" onClick={() => setEditing(null)}>
              Close
            </button>
          </div>
          <div className="mt-4 grid gap-3">
            <input
              className="field mt-0"
              aria-label="Doctor name"
              value={editing.fullName}
              onChange={(event) =>
                setEditing({ ...editing, fullName: event.target.value })
              }
            />
            <textarea
              className="field mt-0 min-h-24"
              aria-label="Doctor biography"
              value={editing.bio ?? ""}
              onChange={(event) =>
                setEditing({ ...editing, bio: event.target.value })
              }
            />
            <button
              className="btn-primary w-fit"
              disabled={save.isPending}
              onClick={() => save.mutate()}
            >
              Save clinician
            </button>
          </div>
        </div>
      )}
      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {doctors.data?.map((doctor) => (
          <article key={doctor.id} className="workspace-card">
            <p className="font-serif text-xl text-[#143d3c]">
              {doctor.fullName}
            </p>
            <p className="mt-1 text-sm text-[#5d9185]">{doctor.specialty}</p>
            <p className="mt-4 min-h-10 text-sm leading-6 text-slate-500">
              {doctor.bio || "No profile description yet."}
            </p>
            <div className="mt-5 flex gap-2">
              <button
                className="workspace-action"
                onClick={() => setEditing(doctor)}
              >
                Edit profile
              </button>
              <button
                className="workspace-action"
                disabled={active.isPending}
                onClick={() => active.mutate(doctor)}
              >
                {doctor.active ? "Deactivate" : "Activate"}
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
function AdminSchedulingControls() {
  const doctors = useQuery({
    queryKey: ["admin-doctors"],
    queryFn: clinicApi.adminDoctors,
  });
  const [doctorId, setDoctorId] = useState("");
  const [day, setDay] = useState("1");
  const [start, setStart] = useState("09:00");
  const [end, setEnd] = useState("17:00");
  const [offStart, setOffStart] = useState("");
  const [offEnd, setOffEnd] = useState("");
  const [reason, setReason] = useState("");
  const [editingOffId, setEditingOffId] = useState<string | null>(null);
  const hours = useQuery({
    queryKey: ["schedule-hours", doctorId],
    queryFn: () => clinicApi.workingHours(doctorId),
    enabled: Boolean(doctorId),
  });
  const timeOff = useQuery({
    queryKey: ["schedule-time-off", doctorId],
    queryFn: () => clinicApi.timeOff(doctorId),
    enabled: Boolean(doctorId),
  });
  useEffect(() => {
    if (!doctorId && doctors.data?.[0]) setDoctorId(doctors.data[0].id);
  }, [doctorId, doctors.data]);
  const refresh = async () => {
    await Promise.all([
      queryClient.refetchQueries({ queryKey: ["schedule-hours", doctorId] }),
      queryClient.refetchQueries({ queryKey: ["schedule-time-off", doctorId] }),
    ]);
  };
  const saveHours = useMutation({
    mutationFn: async () => {
      await clinicApi.csrf();
      return clinicApi.replaceWorkingHours(doctorId, Number(day), start, end);
    },
    onMutate: () => {
      queryClient.setQueryData<WorkingHours[]>(
        ["schedule-hours", doctorId],
        (current) =>
          current?.map((item) =>
            item.dayOfWeek === Number(day)
              ? { ...item, startTime: start, endTime: end }
              : item,
          ),
      );
    },
    onSettled: refresh,
  });
  const addOff = useMutation({
    mutationFn: async () => {
      await clinicApi.csrf();
      return clinicApi.addTimeOff(
        doctorId,
        new Date(offStart).toISOString(),
        new Date(offEnd).toISOString(),
        reason,
      );
    },
    onSuccess: (id) => {
      queryClient.setQueryData<TimeOff[]>(
        ["schedule-time-off", doctorId],
        (current) => [
          ...(current ?? []),
          {
            id,
            startsAt: new Date(offStart).toISOString(),
            endsAt: new Date(offEnd).toISOString(),
            reason: reason || null,
          },
        ],
      );
      setReason("");
      setOffStart("");
      setOffEnd("");
      refresh();
    },
  });
  const updateOff = useMutation({
    mutationFn: async () => {
      if (!editingOffId) return;
      await clinicApi.csrf();
      return clinicApi.updateTimeOff(
        editingOffId,
        new Date(offStart).toISOString(),
        new Date(offEnd).toISOString(),
        reason,
      );
    },
    onMutate: () => {
      queryClient.setQueryData<TimeOff[]>(
        ["schedule-time-off", doctorId],
        (current) =>
          current?.map((item) =>
            item.id === editingOffId
              ? {
                  ...item,
                  startsAt: new Date(offStart).toISOString(),
                  endsAt: new Date(offEnd).toISOString(),
                  reason: reason || null,
                }
              : item,
          ),
      );
    },
    onSettled: () => {
      setEditingOffId(null);
      setReason("");
      setOffStart("");
      setOffEnd("");
      refresh();
    },
  });
  const removeOff = useMutation({
    mutationFn: async (id: string) => {
      await clinicApi.csrf();
      return clinicApi.deleteTimeOff(id);
    },
    onMutate: (id) => {
      queryClient.setQueryData<TimeOff[]>(
        ["schedule-time-off", doctorId],
        (current) => current?.filter((item) => item.id !== id),
      );
    },
    onSettled: refresh,
  });
  const localInputValue = (value: string) => {
    const date = new Date(value);
    date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
    return date.toISOString().slice(0, 16);
  };
  const dayNames = [
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
  ];
  const invalidHours = !end || end <= start;
  const invalidTimeOff = !offStart || !offEnd || offEnd <= offStart;
  return (
    <section className="workspace-section">
      <p className="eyebrow">Individual availability</p>
      <h2 className="mt-2 font-serif text-3xl text-[#143d3c]">
        Working hours and time off
      </h2>
      <p className="mt-2 text-sm text-slate-500">
        Select one clinician, then make a clear change below.
      </p>
      <label className="mt-5 block max-w-xl text-sm font-medium text-slate-700">
        Clinician
        <select
          className="field mt-2"
          value={doctorId}
          onChange={(event) => {
            setDoctorId(event.target.value);
            setEditingOffId(null);
            setOffStart("");
            setOffEnd("");
            setReason("");
          }}
        >
          {doctors.data?.map((doctor) => (
            <option key={doctor.id} value={doctor.id}>
              {doctor.fullName} · {doctor.specialty}
            </option>
          ))}
        </select>
      </label>
      <div className="mt-6 grid gap-7 lg:grid-cols-2">
        <div className="workspace-card">
          <h3 className="font-serif text-xl text-[#143d3c]">
            Weekly working hours
          </h3>
          <p className="mt-1 text-sm text-slate-500">
            Click Edit beside a day, change the hours, then save.
          </p>
          <div className="mt-4 grid gap-2">
            {hours.data?.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between rounded-lg bg-white p-3 text-sm"
              >
                <span className="font-medium">
                  {dayNames[item.dayOfWeek - 1]}
                </span>
                <span>
                  {item.startTime}–{item.endTime}
                </span>
                <button
                  className="workspace-action"
                  onClick={() => {
                    setDay(String(item.dayOfWeek));
                    setStart(item.startTime);
                    setEnd(item.endTime);
                  }}
                >
                  Edit
                </button>
              </div>
            ))}
          </div>
          <div className="mt-5 grid gap-2 sm:grid-cols-3">
            <label className="text-xs text-slate-500">
              Day
              <select
                className="field mt-1"
                value={day}
                onChange={(event) => setDay(event.target.value)}
              >
                {dayNames.map((name, index) => (
                  <option key={name} value={index + 1}>
                    {name}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs text-slate-500">
              Starts
              <input
                className="field mt-1"
                type="time"
                value={start}
                onChange={(event) => setStart(event.target.value)}
              />
            </label>
            <label className="text-xs text-slate-500">
              Ends
              <input
                className="field mt-1"
                type="time"
                value={end}
                onChange={(event) => setEnd(event.target.value)}
              />
            </label>
          </div>
          <button
            className="btn-primary mt-4"
            disabled={!doctorId || invalidHours || saveHours.isPending}
            onClick={() => saveHours.mutate()}
          >
            Save {dayNames[Number(day) - 1]} hours
          </button>
        </div>
        <div className="workspace-card">
          <h3 className="font-serif text-xl text-[#143d3c]">Time off</h3>
          <p className="mt-1 text-sm text-slate-500">
            Add an absence, or choose Edit on an existing entry.
          </p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="text-xs text-slate-500">
              From
              <input
                className="field mt-1"
                type="datetime-local"
                value={offStart}
                onChange={(event) => setOffStart(event.target.value)}
              />
            </label>
            <label className="text-xs text-slate-500">
              Until
              <input
                className="field mt-1"
                type="datetime-local"
                value={offEnd}
                onChange={(event) => setOffEnd(event.target.value)}
              />
            </label>
          </div>
          <label className="mt-3 block text-xs text-slate-500">
            Reason (optional)
            <input
              className="field mt-1"
              placeholder="e.g. Annual leave"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          {!invalidTimeOff && (
            <div className="mt-4 flex flex-wrap gap-2">
              <button
                className="btn-primary"
                disabled={!doctorId || addOff.isPending}
                onClick={() => addOff.mutate()}
              >
                Add time off
              </button>
              {editingOffId && (
                <button
                  className="workspace-action"
                  disabled={updateOff.isPending}
                  onClick={() => updateOff.mutate()}
                >
                  Save changes to time off
                </button>
              )}
            </div>
          )}
          {offStart && offEnd && offEnd <= offStart && (
            <p className="mt-2 text-sm text-amber-700">
              Until must be after From.
            </p>
          )}
          <div className="mt-5 grid gap-2">
            {timeOff.data?.length ? (
              timeOff.data.map((item) => (
                <div key={item.id} className="rounded-lg bg-white p-3 text-sm">
                  <p className="font-medium text-[#143d3c]">
                    {new Date(item.startsAt).toLocaleString()} →{" "}
                    {new Date(item.endsAt).toLocaleString()}
                  </p>
                  <p className="mt-1 text-slate-500">
                    {item.reason || "No reason added"}
                  </p>
                  <div className="mt-3 flex gap-2">
                    <button
                      className="workspace-action"
                      onClick={() => {
                        setEditingOffId(item.id);
                        setOffStart(localInputValue(item.startsAt));
                        setOffEnd(localInputValue(item.endsAt));
                        setReason(item.reason ?? "");
                      }}
                    >
                      Edit
                    </button>
                    <button
                      className="workspace-action"
                      disabled={removeOff.isPending}
                      onClick={() => removeOff.mutate(item.id)}
                    >
                      Remove
                    </button>
                  </div>
                </div>
              ))
            ) : (
              <p className="text-sm text-slate-500">
                No time off recorded for this clinician.
              </p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
function AdminServiceCreator() {
  const [name, setName] = useState("");
  const [duration, setDuration] = useState("30");
  const [price, setPrice] = useState("0");
  const create = useMutation({
    mutationFn: async () => {
      await clinicApi.csrf();
      return clinicApi.createService(
        name,
        Number(duration),
        Math.round(Number(price) * 100),
      );
    },
    onSuccess: async () => {
      setName("");
      setDuration("30");
      setPrice("0");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin-services"] }),
        queryClient.invalidateQueries({ queryKey: ["assignment-services"] }),
        queryClient.invalidateQueries({ queryKey: ["services"] }),
      ]);
    },
  });
  return (
    <section className="mt-7 rounded-[1.5rem] border border-[#dce8e1] bg-white p-6">
      <p className="eyebrow">Service management</p>
      <h2 className="mt-2 font-serif text-2xl text-[#143d3c]">Add a service</h2>
      <div className="mt-5 grid gap-3 md:grid-cols-4">
        <input
          className="field mt-0"
          placeholder="Service name"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
        <input
          className="field mt-0"
          type="number"
          min="5"
          max="480"
          placeholder="Minutes"
          value={duration}
          onChange={(event) => setDuration(event.target.value)}
        />
        <input
          className="field mt-0"
          type="number"
          min="0"
          step="0.01"
          placeholder="Price"
          value={price}
          onChange={(event) => setPrice(event.target.value)}
        />
        <button
          className="btn-primary justify-center"
          disabled={!name || create.isPending}
          onClick={() => create.mutate()}
        >
          Add service
        </button>
      </div>
    </section>
  );
}
function OperationsWaitlist() {
  const entries=useQuery({queryKey:["operations-waitlist"],queryFn:clinicApi.operationsWaitlist});
  const [offerTimes,setOfferTimes]=useState<Record<string,string>>({});
  const offer=useMutation({mutationFn:async({id,startsAt}:{id:string;startsAt:string})=>{await clinicApi.csrf();return clinicApi.offerWaitlist(id,new Date(startsAt).toISOString());},onSuccess:()=>void queryClient.invalidateQueries({queryKey:["operations-waitlist"]})});
  return <section className="bg-[#f5f7f5] px-5 pb-12 md:px-10"><div className="mx-auto max-w-[1250px] rounded-[1.5rem] border border-[#dce8e1] bg-white p-6"><p className="eyebrow">Waitlist operations</p><h2 className="mt-2 font-serif text-2xl text-[#143d3c]">Offer newly available openings</h2><p className="mt-1 text-sm text-slate-500">Choose a real opening for the requested clinician. An offer never reserves it; availability is checked again when the patient books.</p><div className="mt-5 grid gap-3">{entries.data?.length?entries.data.map(item=><div key={item.id} className="workspace-card flex flex-wrap items-center justify-between gap-3"><div><p className="font-medium text-[#143d3c]">{item.patientName} · {item.serviceName}</p><p className="text-sm text-slate-500">{item.doctorName||"Any clinician — assign a clinician first"}{item.requestedDate?` · ${item.requestedDate}`:""} · {item.contactPreference}</p></div>{item.status==="ACTIVE"?<div className="flex flex-wrap gap-2"><input className="field mt-0 w-auto" type="datetime-local" value={offerTimes[item.id]??""} onChange={event=>setOfferTimes({...offerTimes,[item.id]:event.target.value})}/><button className="workspace-action" disabled={offer.isPending||!offerTimes[item.id]||!item.doctorName} onClick={()=>offer.mutate({id:item.id,startsAt:offerTimes[item.id] ?? ""})}>Send opening offer</button></div>:<span className="text-sm font-medium text-[#28584f]">Offer sent</span>}</div>):<p className="rounded-xl bg-[#f6faf7] p-5 text-sm text-slate-500">No active waitlist requests.</p>}</div></div></section>;
}
function StaffWalkInBooking() {
  const [query, setQuery] = useState("");
  const [patient, setPatient] = useState<StaffPatient | null>(null);
  const [newName, setNewName] = useState("");
  const [newPhone, setNewPhone] = useState("");
  const [service, setService] = useState<Service | null>(null);
  const [doctor, setDoctor] = useState<Doctor | null>(null);
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [slot, setSlot] = useState<Slot | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const patients = useQuery({ queryKey: ["staff-patients", query], queryFn: () => clinicApi.staffPatients(query), enabled: query.trim().length > 1 });
  const services = useQuery({ queryKey: ["services"], queryFn: clinicApi.services });
  const doctors = useQuery({ queryKey: ["staff-booking-doctors", service?.id], queryFn: () => clinicApi.doctors(service!.id), enabled: Boolean(service) });
  const slots = useQuery({ queryKey: ["staff-booking-slots", doctor?.id, service?.id, date], queryFn: () => clinicApi.slots(doctor!.id, service!.id, date), enabled: Boolean(doctor && service) });
  const createPatient = useMutation({ mutationFn: async () => { await clinicApi.csrf(); return clinicApi.createWalkInPatient(newName, newPhone); }, onSuccess: (id) => { setPatient({ id, fullName: newName, phone: newPhone || null }); setNewName(""); setNewPhone(""); setMessage("Walk-in patient created. Choose their care below."); } });
  const book = useMutation({ mutationFn: async () => { await clinicApi.csrf(); return clinicApi.book({ patientId: patient!.id, serviceId: service!.id, doctorId: doctor!.id, startsAt: slot!.startsAt }); }, onSuccess: async () => { setMessage("Appointment request created for this patient."); setSlot(null); await queryClient.invalidateQueries({ queryKey: ["staff-actions"] }); await queryClient.invalidateQueries({ queryKey: ["workspace-appointments"] }); }, onError: (error) => setMessage(error instanceof ApiError ? error.message : "We could not book that time.") });
  return <section className="bg-[#f5f7f5] px-5 pb-12 md:px-10"><div className="mx-auto max-w-[1250px] rounded-[1.5rem] border border-[#dce8e1] bg-white p-6"><p className="eyebrow">Phone and walk-in booking</p><h2 className="mt-2 font-serif text-2xl text-[#143d3c]">Book on behalf of a patient</h2><p className="mt-1 text-sm text-slate-500">Search first. If they do not have an account, create a walk-in record without a login.</p><div className="mt-5 grid gap-5 lg:grid-cols-2"><div className="workspace-card"><label className="text-sm font-medium">Find patient<input className="field mt-2" placeholder="Name or phone" value={query} onChange={(event) => setQuery(event.target.value)} /></label><div className="mt-3 grid gap-2">{patients.data?.map(item => <button key={item.id} className={`choice text-left ${patient?.id === item.id ? "choice-active" : ""}`} onClick={() => setPatient(item)}><b>{item.fullName}</b><span className="block text-sm text-slate-500">{item.phone || "No phone"}</span></button>)}</div><div className="mt-5 border-t border-[#e1ebe5] pt-4"><p className="text-sm font-medium">New walk-in / phone patient</p><input className="field mt-2" placeholder="Full name" value={newName} onChange={(event) => setNewName(event.target.value)} /><input className="field mt-2" placeholder="Phone (optional)" value={newPhone} onChange={(event) => setNewPhone(event.target.value)} /><button className="btn-quiet mt-3" disabled={!newName.trim() || createPatient.isPending} onClick={() => createPatient.mutate()}>Create patient record</button></div></div><div className="workspace-card"><p className="text-sm font-medium">{patient ? `Booking for ${patient.fullName}` : "Select or create a patient first"}</p><select className="field mt-3" value={service?.id ?? ""} onChange={(event) => { setService(services.data?.find(item => item.id === event.target.value) ?? null); setDoctor(null); setSlot(null); }}><option value="">Choose service</option>{services.data?.map(item => <option key={item.id} value={item.id}>{item.name} · ${(item.priceCents / 100).toFixed(2)}</option>)}</select>{service && <p className="mt-2 text-sm font-medium text-[#28584f]">Appointment price: ${(service.priceCents / 100).toFixed(2)}</p>}{service && <select className="field mt-3" value={doctor?.id ?? ""} onChange={(event) => { setDoctor(doctors.data?.find(item => item.id === event.target.value) ?? null); setSlot(null); }}><option value="">Choose clinician</option>{doctors.data?.map(item => <option key={item.id} value={item.id}>{item.fullName} · {item.specialty}</option>)}</select>}{doctor && <><input className="date-input mt-3" type="date" value={date} onChange={(event) => { setDate(event.target.value); setSlot(null); }} /><div className="mt-3 flex flex-wrap gap-2">{slots.data?.map(item => <button key={item.startsAt} className={`slot ${slot?.startsAt === item.startsAt ? "slot-active" : ""}`} onClick={() => setSlot(item)}>{new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(item.startsAt))}</button>)}</div></>}<button className="btn-primary mt-5" disabled={!patient || !slot || book.isPending} onClick={() => book.mutate()}>{book.isPending ? "Booking…" : service ? `Create appointment request · $${(service.priceCents / 100).toFixed(2)}` : "Create appointment request"}</button></div></div>{message && <p role="status" className="mt-4 rounded-xl bg-[#e6f3eb] p-3 text-sm text-[#28584f]">{message}</p>}</div></section>;
}
function StaffAppointmentActions() {
  const [date, setDate] = useState("");
  const appointments = useQuery({
    queryKey: ["staff-actions", date],
    queryFn: () => clinicApi.workspaceAppointments(date || undefined),
    refetchInterval: 5000,
  });
  const action = useMutation({
    mutationFn: async ({
      item,
      status,
    }: {
      item: WorkspaceAppointment;
      status: string;
    }) => {
      await clinicApi.csrf();
      return clinicApi.updateAppointmentStatus(item.id, item.version, status);
    },
    onMutate: ({ item, status }) => updateAppointmentCaches(item, status),
    onSettled: refreshAppointmentCaches,
  });
  const dateTime = (value: string) =>
    new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(value));
  return (
    <section className="bg-[#f5f7f5] px-5 pb-12 md:px-10">
      <div className="mx-auto max-w-[1250px] rounded-[1.5rem] border border-[#dce8e1] bg-white p-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Staff actions</p>
            <h2 className="mt-2 font-serif text-2xl text-[#143d3c]">
              Confirm or cancel requests
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              All upcoming appointments are shown. Use the calendar to filter
              one day.
            </p>
          </div>
          <input
            className="date-input"
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </div>
        <div className="mt-5 grid gap-3">
          {appointments.data
            ?.filter(
              (item) =>
                item.status === "PENDING" || item.status === "CONFIRMED",
            )
            .map((item) => (
              <div
                key={item.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[#f7faf8] p-4"
              >
                <div>
                  <p className="font-medium text-[#143d3c]">
                    {item.patientName} · {item.serviceName}
                  </p>
                  <p className="text-sm text-slate-500">
                    {item.doctorName} · {dateTime(item.startsAt)}
                  </p>
                </div>
                <div className="flex gap-2">
                  {item.status === "PENDING" && (
                    <button
                      className="workspace-action"
                      disabled={action.isPending}
                      onClick={() =>
                        action.mutate({ item, status: "CONFIRMED" })
                      }
                    >
                      Confirm
                    </button>
                  )}
                  <button
                    className="workspace-action"
                    disabled={action.isPending}
                    onClick={() => action.mutate({ item, status: "CANCELLED" })}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ))}
        </div>
      </div>
    </section>
  );
}
function DoctorAppointmentActions() {
  const [date, setDate] = useState("");
  const appointments = useQuery({
    queryKey: ["doctor-actions", date],
    queryFn: () => clinicApi.workspaceAppointments(date || undefined),
    refetchInterval: 5000,
  });
  const action = useMutation({
    mutationFn: async ({
      item,
      status,
    }: {
      item: WorkspaceAppointment;
      status: string;
    }) => {
      await clinicApi.csrf();
      return clinicApi.updateAppointmentStatus(item.id, item.version, status);
    },
    onMutate: ({ item, status }) => updateAppointmentCaches(item, status),
    onSettled: refreshAppointmentCaches,
  });
  const dateTime = (value: string) =>
    new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(value));
  return (
    <section className="bg-[#f5f7f5] px-5 pb-12 md:px-10">
      <div className="mx-auto max-w-[1250px] rounded-[1.5rem] border border-[#dce8e1] bg-white p-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">Visit actions</p>
            <h2 className="mt-2 font-serif text-2xl text-[#143d3c]">
              Complete your visits
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              All confirmed visits are shown. Use the calendar to filter one
              day.
            </p>
          </div>
          <input
            className="date-input"
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </div>
        <div className="mt-5 grid gap-3">
          {appointments.data
            ?.filter((item) => item.status === "CONFIRMED")
            .map((item) => (
              <div
                key={item.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[#f7faf8] p-4"
              >
                <div>
                  <p className="font-medium text-[#143d3c]">
                    {item.patientName} · {item.serviceName}
                  </p>
                  <p className="text-sm text-slate-500">
                    {dateTime(item.startsAt)}
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    className="workspace-action"
                    disabled={action.isPending}
                    onClick={() => action.mutate({ item, status: "COMPLETED" })}
                  >
                    Complete
                  </button>
                  <button
                    className="workspace-action"
                    disabled={action.isPending}
                    onClick={() => action.mutate({ item, status: "NO_SHOW" })}
                  >
                    No-show
                  </button>
                </div>
              </div>
            ))}
        </div>
      </div>
    </section>
  );
}
function DoctorServiceAssignments() {
  const doctors = useQuery({
    queryKey: ["admin-doctors"],
    queryFn: clinicApi.adminDoctors,
  });
  const [doctorId, setDoctorId] = useState("");
  const services = useQuery({
    queryKey: ["admin-services"],
    queryFn: clinicApi.adminServices,
  });
  const assigned = useQuery({
    queryKey: ["doctor-service-ids", doctorId],
    queryFn: () => clinicApi.doctorServiceIds(doctorId),
    enabled: Boolean(doctorId),
  });
  const [selected, setSelected] = useState<string[]>([]);
  const [saved, setSaved] = useState(false);
  useEffect(() => {
    if (!doctorId && doctors.data?.[0]) setDoctorId(doctors.data[0].id);
  }, [doctorId, doctors.data]);
  useEffect(() => {
    if (assigned.data) setSelected(assigned.data);
  }, [assigned.data]);
  const save = useMutation({
    mutationFn: async () => {
      await clinicApi.csrf();
      return clinicApi.replaceDoctorServices(doctorId, selected);
    },
    onSuccess: async () => {
      setSaved(true);
      await queryClient.refetchQueries({
        queryKey: ["doctor-service-ids", doctorId],
      });
    },
  });
  const toggle = (id: string) =>
    setSelected((current) =>
      current.includes(id)
        ? current.filter((value) => value !== id)
        : [...current, id],
    );
  return (
    <section className="workspace-section">
      <p className="eyebrow">Clinical configuration</p>
      <h2 className="mt-2 font-serif text-2xl text-[#143d3c]">
        Assign services to any clinician
      </h2>
      <select
        className="field mt-5 max-w-md"
        value={doctorId}
        onChange={(event) => {
          setDoctorId(event.target.value);
          setSaved(false);
        }}
      >
        {doctors.data?.map((doctor) => (
          <option key={doctor.id} value={doctor.id}>
            {doctor.fullName} · {doctor.specialty}
          </option>
        ))}
      </select>
      <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {services.data?.map((service) => (
          <label
            key={service.id}
            className="workspace-card flex cursor-pointer items-center gap-3 text-sm"
          >
            <input
              type="checkbox"
              checked={selected.includes(service.id)}
              onChange={() => {
                setSaved(false);
                toggle(service.id);
              }}
            />
            <span>
              <b className="text-[#143d3c]">{service.name}</b>
              <small className="block text-slate-500">
                {service.durationMinutes} minutes · ${(service.priceCents / 100).toFixed(2)}
              </small>
            </span>
          </label>
        ))}
      </div>
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <button
          className="btn-primary"
          disabled={!doctorId || save.isPending}
          onClick={() => save.mutate()}
        >
          {save.isPending ? "Saving…" : "Save clinician services"}
        </button>
        {saved && (
          <p
            role="status"
            className="rounded-lg bg-[#e6f3eb] px-3 py-2 text-sm font-medium text-[#28584f]"
          >
            Saved successfully.
          </p>
        )}
      </div>
    </section>
  );
}
function Booking({ user, onAuth }: { user: User | null; onAuth: () => void }) {
  const sectionRef = useScrollReveal<HTMLElement>();
  const [service, setService] = useState<Service | null>(null);
  const [doctor, setDoctor] = useState<Doctor | null>(null);
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [slot, setSlot] = useState<Slot | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const services = useQuery({
    queryKey: ["services"],
    queryFn: clinicApi.services,
  });
  const doctors = useQuery({
    queryKey: ["doctors", service?.id],
    queryFn: () => clinicApi.doctors(service!.id),
    enabled: !!service,
  });
  const slots = useQuery({
    queryKey: ["slots", doctor?.id, service?.id, date],
    queryFn: () => clinicApi.slots(doctor!.id, service!.id, date),
    enabled: !!doctor && !!service,
  });
  const booking = useMutation({
    mutationFn: async () => {
      await clinicApi.csrf();
      return clinicApi.book({
        patientId: user!.patientId!,
        doctorId: doctor!.id,
        serviceId: service!.id,
        startsAt: slot!.startsAt,
      });
    },
    onSuccess: async () => {
      await queryClient.refetchQueries({
        queryKey: ["appointments", user?.id],
      });
      setMessage(
        "Your appointment request is received and appears in My appointments below.",
      );
    },
    onError: (error) =>
      setMessage(
        error instanceof ApiError
          ? error.message
          : "We couldn’t reserve that time. Please choose another.",
      ),
  });
  const waitlist = useMutation({ mutationFn: async () => { await clinicApi.csrf(); return clinicApi.joinWaitlist({ serviceId: service!.id, doctorId: doctor?.id, requestedDate: date, contactPreference: "EMAIL" }); }, onSuccess: () => setMessage("You are on the waitlist. We will contact you if an opening becomes available."), onError: (error) => setMessage(error instanceof ApiError ? error.message : "We could not add you to the waitlist.") });
  return (
    <section
      ref={sectionRef}
      id="booking"
      className="scroll-reveal border-y border-[#dfe8e2] bg-white/60 py-20"
    >
      <div className="mx-auto max-w-7xl px-5">
        <div className="mb-10 flex flex-col justify-between gap-5 md:flex-row">
          <div>
            <p className="eyebrow">Reserve your time</p>
            <h2 className="mt-3 font-serif text-4xl text-[#143d3c]">
              A booking flow that feels human.
            </h2>
          </div>
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <span className="step-dot">1</span>Care{" "}
            <span className="step-dot">2</span>Clinician{" "}
            <span className="step-dot">3</span>Time
          </div>
        </div>
        <div className="grid gap-7 lg:grid-cols-[1fr_300px]">
          <div className="space-y-5">
            <section className="booking-card">
              <h3>1. What kind of care are you looking for?</h3>
              {services.isLoading ? (
                <p className="text-slate-500">Preparing your options…</p>
              ) : services.data?.length ? (
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  {services.data.map((item) => (
                    <button
                      key={item.id}
                      onClick={() => {
                        setService(item);
                        setDoctor(null);
                        setSlot(null);
                      }}
                      className={`choice ${service?.id === item.id ? "choice-active" : ""}`}
                    >
                      <span className="font-medium">{item.name}</span>
                      <span className="text-sm text-slate-500">
                        {item.durationMinutes} min · ${(item.priceCents / 100).toFixed(2)}
                      </span>
                    </button>
                  ))}
                </div>
              ) : (
                <Empty label="Services will appear here when the clinic is ready." />
              )}
            </section>
            {service && (
              <section className="booking-card animate-rise">
                <h3>2. Choose your clinician</h3>
                {doctors.isLoading ? (
                  <p className="mt-4 text-slate-500">Finding your care team…</p>
                ) : doctors.data?.length ? (
                  <div className="mt-4 grid gap-3 sm:grid-cols-2">
                    {doctors.data.map((item) => (
                      <button
                        key={item.id}
                        onClick={() => {
                          setDoctor(item);
                          setSlot(null);
                        }}
                        className={`choice text-left ${doctor?.id === item.id ? "choice-active" : ""}`}
                      >
                        <span className="flex items-center gap-3"><ProfileAvatar name={item.fullName} photoUrl={item.photoUrl} className="h-11 w-11 shrink-0" /><span><span className="block font-medium">{item.fullName}</span><span className="block text-sm text-slate-500">{item.specialty}</span></span></span>
                      </button>
                    ))}
                  </div>
                ) : (
                  <Empty label="No clinicians currently offer this service." />
                )}
              </section>
            )}
            {doctor && (
              <section className="booking-card animate-rise">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <h3>3. Find a time that feels right</h3>
                  <input
                    aria-label="Appointment date"
                    className="date-input"
                    type="date"
                    value={date}
                    min={new Date().toISOString().slice(0, 10)}
                    onChange={(event) => {
                      setDate(event.target.value);
                      setSlot(null);
                    }}
                  />
                </div>
                {slots.isLoading ? (
                  <p className="mt-4 text-slate-500">Looking for openings…</p>
                ) : slots.data?.length ? (
                  <div className="mt-4 flex flex-wrap gap-2">
                    {slots.data.map((item) => (
                      <button
                        key={item.startsAt}
                        onClick={() => setSlot(item)}
                        className={`slot ${slot?.startsAt === item.startsAt ? "slot-active" : ""}`}
                      >
                        {new Intl.DateTimeFormat(undefined, {
                          hour: "numeric",
                          minute: "2-digit",
                        }).format(new Date(item.startsAt))}
                      </button>
                    ))}
                  </div>
                ) : (<><Empty label="No available times for this day. Try another date." />{user?.role === "PATIENT" && <button className="btn-quiet mt-3" disabled={waitlist.isPending} onClick={() => waitlist.mutate()}>{waitlist.isPending ? "Joining waitlist…" : "Join waitlist for this day"}</button>}</>)}
              </section>
            )}
          </div>
          <aside className="h-fit rounded-[1.5rem] bg-[#143d3c] p-6 text-white lg:sticky lg:top-24">
            <p className="text-sm text-[#b9d1c9]">Your appointment</p>
            <div className="my-6 h-px bg-white/15" />
            <Summary label="Care" value={service?.name ?? "Choose a service"} />
            <Summary label="Price" value={service ? `$${(service.priceCents / 100).toFixed(2)}` : "Shown after choosing care"} />
            <Summary
              label="Clinician"
              value={doctor?.fullName ?? "Choose a clinician"}
            />
            <Summary
              label="Time"
              value={
                slot
                  ? new Intl.DateTimeFormat(undefined, {
                      dateStyle: "medium",
                      timeStyle: "short",
                    }).format(new Date(slot.startsAt))
                  : "Choose a time"
              }
            />
            {!user ? (
              <button
                className="mt-7 w-full rounded-xl bg-[#dceae3] px-4 py-3 font-medium text-[#143d3c]"
                onClick={onAuth}
              >
                <LogIn size={17} className="mr-2 inline" />
                Sign in to continue
              </button>
            ) : (
              <button
                disabled={!slot || booking.isPending}
                className="mt-7 w-full rounded-xl bg-[#dceae3] px-4 py-3 font-medium text-[#143d3c] disabled:opacity-40"
                onClick={() => booking.mutate()}
              >
                {booking.isPending ? "Reserving…" : service ? `Confirm appointment · $${(service.priceCents / 100).toFixed(2)}` : "Confirm appointment"}
              </button>
            )}
            {message && (
              <p
                role="status"
                className="mt-4 rounded-xl bg-white/10 p-3 text-sm leading-6"
              >
                {message}
              </p>
            )}
          </aside>
        </div>
      </div>
    </section>
  );
}
function Summary({ label, value }: { label: string; value: string }) {
  return (
    <div className="mb-5">
      <p className="text-xs uppercase tracking-[.15em] text-[#9fc0b5]">
        {label}
      </p>
      <p className="mt-1 text-sm">{value}</p>
    </div>
  );
}
function Empty({ label }: { label: string }) {
  return (
    <div className="mt-4 rounded-xl border border-dashed border-[#c6d7d0] p-5 text-sm text-slate-500">
      {label}
    </div>
  );
}
function AppointmentDetailsPanel({ item, details, loading, onClose }: { item: Appointment; details: AppointmentDetails | undefined; loading: boolean; onClose: () => void }) {
  return <section className="mt-5 rounded-2xl bg-white p-5 shadow-sm"><div className="flex flex-wrap items-center justify-between gap-3"><div><p className="eyebrow">Appointment details</p><h3 className="mt-1 font-serif text-2xl text-[#143d3c]">{item.serviceName} with {item.doctorName}</h3></div><button className="btn-quiet" onClick={onClose}>Close</button></div>{loading ? <p className="mt-4 text-sm text-slate-500">Loading appointment details…</p> : details ? <><dl className="mt-5 grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-slate-500">Specialty</dt><dd className="font-medium text-[#143d3c]">{details.specialty}</dd></div><div><dt className="text-slate-500">When</dt><dd className="font-medium text-[#143d3c]">{new Intl.DateTimeFormat(undefined, { dateStyle: "full", timeStyle: "short" }).format(new Date(details.startsAt))}</dd></div><div><dt className="text-slate-500">Duration</dt><dd className="font-medium text-[#143d3c]">{Math.round((new Date(details.endsAt).getTime() - new Date(details.startsAt).getTime()) / 60000)} minutes</dd></div><div><dt className="text-slate-500">Status</dt><dd><span className={`status-badge status-${details.status.toLowerCase()}`}>{details.status.replace("_", " ")}</span></dd></div></dl>{details.status === "CONFIRMED" && <a className="btn-primary mt-5 inline-flex" href={`/api/v1/appointments/${details.id}/calendar`}>Download calendar event</a>}<div className="mt-6 border-t border-[#e1ebe5] pt-4"><h4 className="font-medium text-[#143d3c]">Status timeline</h4>{details.history.length ? <ol className="mt-3 grid gap-2 text-sm text-slate-600">{details.history.map((event, index) => <li key={`${event.changedAt}-${index}`} className="rounded-lg bg-[#f5f7f5] p-3">{event.fromStatus ? `${event.fromStatus} → ` : ""}{event.toStatus} · {new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(event.changedAt))}{event.changedByRole ? ` · ${event.changedByRole.toLowerCase()}` : ""}</li>)}</ol> : <p className="mt-3 text-sm text-slate-500">No status changes recorded yet.</p>}</div></> : <p className="mt-4 text-sm text-rose-700">We could not load these appointment details.</p>}</section>;
}
function MyAppointments({ user }: { user: User | null }) {
  const sectionRef = useScrollReveal<HTMLElement>();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [rescheduling, setRescheduling] = useState<Appointment | null>(null);
  const [rescheduleDate, setRescheduleDate] = useState(new Date().toISOString().slice(0, 10));
  const [newSlot, setNewSlot] = useState<Slot | null>(null);
  const [selected, setSelected] = useState<Appointment | null>(null);
  const appointments = useQuery({
    queryKey: ["appointments", user?.id],
    queryFn: clinicApi.appointments,
    enabled: !!user && user.role === "PATIENT",
    refetchInterval: 5000,
  });
  const waitlistEntries = useQuery({ queryKey: ["my-waitlist"], queryFn: clinicApi.waitlist, enabled: !!user && user.role === "PATIENT" });
  const cancelWaitlist = useMutation({ mutationFn: async (id: string) => { await clinicApi.csrf(); return clinicApi.cancelWaitlist(id); }, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["my-waitlist"] }) });
  const acceptWaitlist = useMutation({ mutationFn: async (id: string) => { await clinicApi.csrf(); return clinicApi.acceptWaitlistOffer(id); }, onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ["my-waitlist"] }); await queryClient.invalidateQueries({ queryKey: ["appointments", user?.id] }); } });
  const cancel = useMutation({
    mutationFn: async (item: Appointment) => {
      await clinicApi.csrf();
      return clinicApi.cancel(item.id, item.version);
    },
    onSuccess: (_, item) => {
      queryClient.setQueryData<Appointment[]>(
        ["appointments", user?.id],
        (current) =>
          current?.map((appointment) =>
            appointment.id === item.id
              ? {
                  ...appointment,
                  status: "CANCELLED",
                  version: appointment.version + 1,
                }
              : appointment,
          ),
      );
      void queryClient.invalidateQueries({
        queryKey: ["appointments", user?.id],
      });
    },
  });
  const availableSlots = useQuery({
    queryKey: ["reschedule-slots", rescheduling?.id, rescheduleDate],
    queryFn: () => clinicApi.slots(rescheduling!.doctorId, rescheduling!.serviceId, rescheduleDate),
    enabled: Boolean(rescheduling),
  });
  const details = useQuery({
    queryKey: ["appointment-details", selected?.id],
    queryFn: () => clinicApi.appointmentDetails(selected!.id),
    enabled: Boolean(selected),
  });
  const reschedule = useMutation({
    mutationFn: async () => {
      await clinicApi.csrf();
      return clinicApi.reschedule(rescheduling!.id, newSlot!.startsAt, rescheduling!.version);
    },
    onSuccess: async () => {
      setRescheduling(null);
      setNewSlot(null);
      await queryClient.invalidateQueries({ queryKey: ["appointments", user?.id] });
    },
  });
  if (!user || user.role !== "PATIENT") return null;
  const upcoming =
    appointments.data?.filter(
      (item) => item.status === "PENDING" || item.status === "CONFIRMED",
    ) ?? [];
  const history =
    appointments.data?.filter(
      (item) => !["PENDING", "CONFIRMED"].includes(item.status),
    ) ?? [];
  const explanation = (status: string) =>
    ({
      PENDING: "Waiting for clinic confirmation",
      CONFIRMED: "Your visit is booked",
      COMPLETED: "Visit completed",
      NO_SHOW: "Marked as not attended",
      CANCELLED: "This appointment was cancelled",
    })[status] ?? status;
  const card = (item: Appointment, canCancel: boolean) => (
    <article
      key={item.id}
      className="flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-white p-5 shadow-sm"
    >
      <div>
        <p className="font-medium text-[#143d3c]">
          {item.serviceName} · {item.doctorName}
        </p>
        <p className="mt-1 text-sm text-slate-500">
          {new Intl.DateTimeFormat(undefined, {
            dateStyle: "medium",
            timeStyle: "short",
          }).format(new Date(item.startsAt))}
        </p>
        <p className="mt-2 text-sm font-medium text-[#28584f]">
          <span className={`status-badge status-${item.status.toLowerCase()}`}>
            {item.status.replace("_", " ")}
          </span>
          <span className="ml-2">{explanation(item.status)}</span>
        </p>
      </div>
      <div className="flex flex-wrap gap-2"><button className="btn-quiet border border-[#b8cbc3]" onClick={() => setSelected(item)}>Details</button>{canCancel && <><button className="btn-quiet border border-[#b8cbc3]" disabled={cancel.isPending} onClick={() => cancel.mutate(item)}>Cancel</button><button className="btn-quiet border border-[#b8cbc3]" onClick={() => { setRescheduling(item); setNewSlot(null); }}>Reschedule</button></>}</div>
    </article>
  );
  return (
    <section
      ref={sectionRef}
      className="scroll-reveal mx-auto max-w-7xl px-5 py-16"
    >
      <div className="appointment-reveal grid gap-7 rounded-[2rem] bg-[#e5efe9] p-7 md:grid-cols-[1fr_auto] md:p-10">
        <div>
          <p className="eyebrow">Your care plan</p>
          <h2 className="mt-3 font-serif text-4xl text-[#143d3c]">
            My appointments
          </h2>
          {waitlistEntries.data?.length ? <div className="mt-5 rounded-2xl bg-white p-5 shadow-sm"><p className="font-serif text-xl text-[#143d3c]">My waitlist requests</p><div className="mt-3 grid gap-2">{waitlistEntries.data.map(entry => <div key={entry.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-[#f5f7f5] p-3 text-sm"><span>{entry.serviceName}{entry.doctorName ? ` · ${entry.doctorName}` : ""}{entry.requestedDate ? ` · ${entry.requestedDate}` : ""} · {entry.status}{entry.offeredStartsAt ? ` · Offered ${new Intl.DateTimeFormat(undefined,{dateStyle:"medium",timeStyle:"short"}).format(new Date(entry.offeredStartsAt))}` : ""}</span><div className="flex gap-2">{entry.status === "OFFERED" && <button className="workspace-action" disabled={acceptWaitlist.isPending} onClick={() => acceptWaitlist.mutate(entry.id)}>Accept offer</button>}{["ACTIVE","OFFERED"].includes(entry.status) && <button className="workspace-action" disabled={cancelWaitlist.isPending} onClick={() => cancelWaitlist.mutate(entry.id)}>Leave waitlist</button>}</div></div>)}</div></div> : null}
          {selected && <AppointmentDetailsPanel item={selected} details={details.data} loading={details.isLoading} onClose={() => setSelected(null)} />}
          {rescheduling && <div className="mt-5 rounded-2xl bg-white p-5 shadow-sm"><div className="flex flex-wrap items-center justify-between gap-3"><div><p className="font-medium text-[#143d3c]">Reschedule {rescheduling.serviceName}</p><p className="text-sm text-slate-500">Choose a new time with {rescheduling.doctorName}. Your current appointment stays unchanged until the new time is successfully reserved.</p></div><button className="btn-quiet" onClick={() => setRescheduling(null)}>Close</button></div><input className="date-input mt-4" type="date" min={new Date().toISOString().slice(0, 10)} value={rescheduleDate} onChange={(event) => { setRescheduleDate(event.target.value); setNewSlot(null); }} />{availableSlots.isLoading ? <p className="mt-4 text-sm text-slate-500">Finding available times…</p> : <div className="mt-4 flex flex-wrap gap-2">{availableSlots.data?.map((slot) => <button key={slot.startsAt} className={`slot ${newSlot?.startsAt === slot.startsAt ? "slot-active" : ""}`} onClick={() => setNewSlot(slot)}>{new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(slot.startsAt))}</button>)}</div>}<button className="btn-primary mt-4" disabled={!newSlot || reschedule.isPending} onClick={() => reschedule.mutate()}>{reschedule.isPending ? "Rescheduling…" : "Confirm new time"}</button></div>}
          {appointments.isLoading ? (
            <p className="mt-5 text-slate-600">Loading your appointments…</p>
          ) : (
            <>
              <div className="mt-6">
                <div className="flex items-center justify-between">
                  <h3 className="font-serif text-2xl text-[#143d3c]">
                    Upcoming appointments
                  </h3>
                  <span className="text-sm text-slate-500">
                    {upcoming.length}
                  </span>
                </div>
                {upcoming.length ? (
                  <div className="mt-3 space-y-3">
                    {upcoming.map((item) => card(item, true))}
                  </div>
                ) : (
                  <p className="mt-3 rounded-xl bg-white/70 p-4 text-sm text-slate-600">
                    You have no upcoming appointments.
                  </p>
                )}
              </div>
              <div className="mt-6 border-t border-[#cadbd3] pt-5">
                <button
                  className="flex w-full items-center justify-between text-left font-serif text-2xl text-[#143d3c]"
                  onClick={() => setHistoryOpen((open) => !open)}
                >
                  Appointment history ({history.length}){" "}
                  <span className="text-lg">{historyOpen ? "▴" : "▾"}</span>
                </button>
                <p className="mt-1 text-sm text-slate-500">
                  Completed, no-show, and cancelled appointments are kept here.
                </p>
                {historyOpen && (
                  <div className="mt-4 space-y-3">
                    {history.length ? (
                      history.map((item) => card(item, false))
                    ) : (
                      <p className="rounded-xl bg-white/70 p-4 text-sm text-slate-600">
                        No appointment history yet.
                      </p>
                    )}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
        <div className="hidden self-end rounded-2xl bg-[#143d3c] p-5 text-sm text-[#dceae3] md:block">
          Your appointments stay private to your account.
        </div>
      </div>
    </section>
  );
}
const schema = z.object({
  fullName: z.string().min(2, "Enter your full name."),
  email: z.string().email("Enter a valid email."),
  password: z
    .string()
    .min(12, "Use at least 12 characters.")
    .regex(/[a-z]/, "Include a lowercase letter.")
    .regex(/[A-Z]/, "Include an uppercase letter.")
    .regex(/\d/, "Include a number.")
    .regex(/[^A-Za-z0-9\s]/, "Include a special character.")
    .refine((value) => !/\s/.test(value), "Do not use spaces."),
});
function AuthDialog({
  mode,
  onClose,
  onSuccess,
}: {
  mode: "login" | "register" | null;
  onClose: () => void;
  onSuccess: (user: User) => void;
}) {
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: "Patient" },
  });
  const [resetMode, setResetMode] = useState(false);
  const [resetLink, setResetLink] = useState<string | null>(null);
  if (!mode) return null;
  const requestReset = async () => {
    const email = form.getValues("email").trim();
    if (!/^\S+@\S+\.\S+$/.test(email)) {
      form.setError("email", { message: "Enter a valid email." });
      return;
    }
    form.clearErrors("email");
    form.clearErrors("root");
    try {
      const link = await clinicApi.requestPasswordReset(email);
      setResetLink(link || null);
    } catch (error) {
      form.setError("root", { message: error instanceof ApiError ? error.message : "The reset link could not be created. Please try again." });
    }
  };
  const submit = form.handleSubmit(async (values) => {
    try {
      const user =
        mode === "register"
          ? await clinicApi.register(values)
          : await clinicApi.login({
              email: values.email,
              password: values.password,
            });
      onSuccess(user);
    } catch (error) {
      form.setError("root", {
        message:
          error instanceof ApiError ? error.message : "Please try again.",
      });
    }
  });
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-[#143d3c]/45 p-4">
      <form
        onSubmit={submit}
        className="w-full max-w-md rounded-[1.75rem] bg-[#fbfaf7] p-7 shadow-2xl"
      >
        <button
          type="button"
          onClick={onClose}
          className="float-right text-slate-500"
        >
          ×
        </button>
        <p className="eyebrow">Your care, your account</p>
        <h2 className="mt-3 font-serif text-3xl text-[#143d3c]">
          {resetMode ? "Reset your password" : mode === "register" ? "Create your account" : "Welcome back"}
        </h2>
        {mode === "register" && (
          <Field
            label="Full name"
            error={form.formState.errors.fullName?.message}
            input={form.register("fullName")}
          />
        )}
        {(mode === "login" || resetMode) && (
          <input type="hidden" {...form.register("fullName")} />
        )}
        <Field
          label="Email"
          error={form.formState.errors.email?.message}
          input={form.register("email")}
          type="email"
        />
        {!resetMode && <Field
          label="Password"
          error={form.formState.errors.password?.message}
          input={form.register("password")}
          type="password"
        />}
        {mode === "register" && (
          <p className="mt-3 text-xs leading-5 text-slate-500">
            12+ characters, including uppercase, lowercase, a number, and a
            special character. No spaces.
          </p>
        )}
        {form.formState.errors.root && (
          <p className="mt-3 text-sm text-red-700">
            {form.formState.errors.root.message}
          </p>
        )}
        {mode === "login" && !resetMode && <button type="button" className="btn-quiet mt-4" onClick={() => { setResetMode(true); setResetLink(null); form.clearErrors(); }}>Forgot password?</button>}
        {resetMode && resetLink && <div role="status" className="mt-4 rounded-xl bg-[#e6f3eb] p-3 text-sm text-[#28584f]"><p className="font-medium">Local reset link ready.</p><p className="mt-1">No email is sent in local development. Copy this link and open it in a new tab:</p><code className="mt-2 block break-all rounded-lg bg-white/70 p-2 text-xs">{window.location.origin}{resetLink}</code><button type="button" className="workspace-action mt-2" onClick={() => navigator.clipboard.writeText(`${window.location.origin}${resetLink}`).catch(() => undefined)}>Copy link</button></div>}
        <button
          className="btn-primary mt-6 w-full justify-center"
          type={resetMode ? "button" : "submit"}
          onClick={resetMode ? () => void requestReset() : undefined}
        >
          {resetMode ? "Request reset link" : mode === "register" ? "Create account" : "Sign in"}
        </button>
      </form>
    </div>
  );
}
function Field({
  label,
  error,
  input,
  type = "text",
}: {
  label: string;
  error?: string;
  input: UseFormRegisterReturn;
  type?: string;
}) {
  return (
    <label className="mt-5 block text-sm font-medium text-slate-700">
      {label}
      <input className="field" type={type} {...input} />
      {error && (
        <span className="mt-1 block text-sm text-red-700">{error}</span>
      )}
    </label>
  );
}
function Trust() {
  return (
    <section id="care" className="mx-auto max-w-7xl px-5 py-20">
      <div className="mb-10 grid gap-6 md:grid-cols-[.8fr_1.2fr] md:items-end">
        <div className="stat-block">
          <span className="eyebrow text-[#dceae3]">Care, considered</span>
          <p className="mt-3 font-serif text-6xl text-white">
            Every visit
            <br />
            <em className="font-normal text-[#b7d8cd]">matters.</em>
          </p>
          <p className="mt-5 max-w-sm text-sm leading-6 text-[#dceae3]">
            Designed around calm, clarity, and the confidence to make time for
            your wellbeing.
          </p>
        </div>
        <div>
          <p className="eyebrow">What stays with you</p>
          <h2 className="mt-3 max-w-2xl font-serif text-4xl leading-tight text-[#143d3c]">
            Small details that make care feel more human.
          </h2>
        </div>
      </div>
      <div className="grid gap-5 md:grid-cols-3">
        {[
          [
            Clock3,
            "Time, considered",
            "A thoughtful booking experience that respects your day.",
          ],
          [
            ShieldCheck,
            "Care, protected",
            "Private access and clear account controls at every step.",
          ],
          [
            Check,
            "Details, handled",
            "Calm confirmation and useful information when it matters.",
          ],
        ].map(([Icon, title, text]) => {
          const I = Icon as typeof Clock3;
          return (
            <article
              key={String(title)}
              className="trust-card rounded-[1.5rem] bg-white p-7 shadow-sm"
            >
              <I className="text-[#6e958b]" />
              <h3 className="mt-7 font-serif text-2xl text-[#143d3c]">
                {String(title)}
              </h3>
              <p className="mt-3 leading-7 text-slate-600">{String(text)}</p>
              <span className="mt-8 inline-block text-sm font-medium text-[#28584f]">
                Explore care →
              </span>
            </article>
          );
        })}
      </div>
    </section>
  );
}
function Footer() {
  return (
    <footer className="site-footer">
      <div className="mx-auto grid max-w-7xl gap-10 px-5 py-14 md:grid-cols-[1.2fr_.8fr_.9fr]">
        <div>
          <div className="flex items-center gap-2 font-serif text-2xl text-white">
            <HeartPulse size={22} className="text-[#a8d1c3]" />
            PulseClinic
          </div>
          <p className="mt-5 max-w-sm text-sm leading-7 text-[#c8ddd6]">
            A more composed, personal way to arrange care. Built to make the
            next step feel easier.
          </p>
        </div>
        <div>
          <p className="footer-label">Explore</p>
          <div className="mt-5 grid gap-3 text-sm text-[#dceae3]">
            <a href="#booking">Book care</a>
            <a href="#care">Our approach</a>
            <a href="#top">Your account</a>
          </div>
        </div>
        <div>
          <p className="footer-label">A note on availability</p>
          <p className="mt-5 text-sm leading-7 text-[#c8ddd6]">
            Availability reflects clinic hours and updates as appointments are
            reserved.
          </p>
        </div>
      </div>
      <div className="border-t border-white/10 px-5 py-5 text-center text-xs text-[#a8c3ba]">
        © 2026 PulseClinic · Your care stays private to your account.
      </div>
    </footer>
  );
}
