export default function Home() {
  return (
    <main
      style={{
        minHeight: "100dvh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: "0.5rem",
        padding: "1.5rem",
        textAlign: "center",
      }}
    >
      <h1>QR Menü</h1>
      <p>Bu sayfa yalnızca bir yer tutucudur — gerçek akış bir masadaki QR kodunu okutmakla `/t/[token]` üzerinden başlar.</p>
    </main>
  );
}
